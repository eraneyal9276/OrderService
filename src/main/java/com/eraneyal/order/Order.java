package com.eraneyal.order;

import akka.Done;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.pattern.StatusReply;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.RecoveryCompleted;
import akka.persistence.typed.javadsl.CommandHandlerWithReply;
import akka.persistence.typed.javadsl.CommandHandlerWithReplyBuilderByState;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehavior;
import akka.persistence.typed.javadsl.EventSourcedBehaviorWithEnforcedReplies;
import akka.persistence.typed.javadsl.ReplyEffect;
import akka.persistence.typed.javadsl.RetentionCriteria;
import akka.persistence.typed.javadsl.SignalHandler;
import akka.serialization.jackson.CborSerializable;
import akka.stream.Materializer;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
  * Represents an event sourced Order entity.
  */

public class Order
       extends EventSourcedBehaviorWithEnforcedReplies<Order.Command, Order.Event, Order.State>
{

// ------------------------------------------------------------
// The Commands supported by this entity
// ------------------------------------------------------------

/**
  * Represents the commands supported by this entity.
  */

    public sealed interface Command extends CborSerializable {}

/**
  * Represents a command to validate and persist a new Order.
  * <p>
  * @param items the items of the order, indexed by item identifier
  * @param customer the customer of the order
  * @param replyTo a reference to the actor that will receive acknowledgement for
  * 	   successful or failed processing
  */

    record ReceiveOrder (Map<String,OrderItem> items, Customer customer, ActorRef<StatusReply<Done>> replyTo)
    implements Command {}

/**
  * Represents a command to persist order allocations.
  * <p>
  * @param allocations the order allocations
  */

    record ReceiveOrderAllocations (Map<String,Allocation> allocations)
    implements Command {}

/**
  * Represents a command to mark the items of an order allocation as packed, and book
  * delivery for them.
  * <p>
  * @param allocationId the allocation identifier
  * @param replyTo a reference to the actor that will receive the reply for successful
  *                or failed processing
  */

    record PackOrderAllocation (String allocationId, ActorRef<StatusReply<PackOrderAllocationResult>> replyTo)
    implements Command {}

/**
  * Represents the response to a pack order allocation request.
  * <p>
  * @param trackingId the delivery tracking identifier returned by the courier API
  */

    record PackOrderAllocationResult (String trackingId) implements CborSerializable {}

/**
  * Represents the result of a book delivery courier API call.
  */

    private sealed interface BookDeliveryResult {}

/**
  * Represents success result of a book delivery courier API call.
  * <p>
  * @param trackingId the delivery tracking identifier returned by the courier API
  */

    private record BookDeliverySuccess (String trackingId) implements BookDeliveryResult {}

/**
  * Represents failure result of a book delivery courier API call.
  * <p>
  * @param reason the reason to the failure
  */

    private record BookDeliveryFailure (String reason) implements BookDeliveryResult {}

/**
  * Represents a command to complete the processing of the PackOrderAllocation command
  * after the call to the courier booking API finishes with success or failure.
  * <p>
  * @param allocationId the allocation identifier
  * @param result the result of the book delivery courier API call
  * @param replyTo a reference to the actor that sent the original PackOrderAllocation
  * 	   command, that will receive the reply for successful or failed processing
  */

    private record WrappedPackOrderAllocationResult (
        String allocationId,
        BookDeliveryResult result,
        ActorRef<StatusReply<PackOrderAllocationResult>> replyTo)
    implements Command {}

/**
  * Represents a command to update the tracking status of an order allocation.
  * <p>
  * @param allocationId the allocation identifier
  * @param status the new tracking status
  * @param replyTo a reference to the actor that will receive acknowledgement for
  * 	   successful or failed processing
  */

    record UpdateTracking (String allocationId, Allocation.Status status, ActorRef<StatusReply<Done>> replyTo)
    implements Command {}

/**
  * Represents a command to fetch order details along with its allocations.
  * <p>
  * @param replyTo a reference to the actor that will receive the order details reply
  */

    record FetchOrderDetails (ActorRef<OrderDetails> replyTo) implements Command {}

/**
  * Represents an order's details, along with its allocations.
  * <p>
  * @param allocations the order allocations
  * @param customer the customer details
  */

    record OrderDetails (Map<String,Allocation> allocations, Customer customer)
    implements CborSerializable
    {

/**
  * Returns true if an allocation exists for the given identifier.
  * <p>
  * @param ident the order allocation identifier
  * @return true if an allocation exists for the given identifier
  */

        public boolean hasAllocation (String ident)
        {
            return allocations.containsKey (ident);
        }

 /**
   * Returns the allocation matching the given identifier, or null if it doesn't exist.
   * <p>
   * @param ident the order allocation identifier
   * @return the allocation matching the given identifier, or null if it doesn't exist
   */

        public Allocation getAllocation (String ident)
        {
            return allocations.get (ident);
        }

    }

// ------------------------------------------------------------
// The Events persisted by this entity
// ------------------------------------------------------------

/**
  * Represents the events persisted by this entity.
  */

    public sealed interface Event extends CborSerializable
    {

/**
  * Returns the order identifier.
  * <p>
  * @return the order identifier
  */

        String orderId ();

    }

/**
  * Represents a new order.
  * <p>
  * @param orderId the order identifier
  * @param items the items of the order, indexed by item identifier
  * @param customer the customer of the order
  */

    record OrderReceived (String orderId, Map<String,OrderItem> items, Customer customer)
    implements Event {}

/**
  * Represents received order allocations.
  * <p>
  * @param orderId the order identifier
  * @param allocations the order allocations indexed by allocation identifier
  */

    record OrderAllocationsReceived (String orderId, Map<String,Allocation> allocations)
    implements Event {}

/**
  * Represents an order allocation whose items are marked as packed and booked for
  * delivery.
  * <p>
  * @param orderId the order identifier
  * @param allocationId the allocation identifier
  * @param trackingId the tracking identifier returned by the courier API
  * @param timestamp the timestamp of the packing
  */

    record OrderAllocationPacked (String orderId, String allocationId, String trackingId, Instant timestamp)
    implements Event {}

/**
  * Represents an updated tracking status of an allocation.
  * <p>
  * @param orderId the order identifier
  * @param allocationId the allocation identifier
  * @param status the new tracking status
  * @param timestamp the timestamp of the status update
  */

    record TrackingUpdated (String orderId, String allocationId, Allocation.Status status, Instant timestamp)
    implements Event {}

// ------------------------------------------------------------
// The entity's State
// ------------------------------------------------------------

/**
  * Represents the entity's state.
  */

    public sealed interface State extends CborSerializable
    {

/**
  * Returns the order state as order details.
  * <p>
  * @return the order state as order details
  */

        public OrderDetails toOrderDetails ();

    }

/**
  * Represents an empty order state.
  */

    record BlankState () implements State
    {

/**
  * Returns the order state as order details.
  * <p>
  * @return the order state as order details
  */

        public OrderDetails toOrderDetails ()
        {
// -- returns empty details
            return new OrderDetails (new HashMap<> (), null);
        }

    }

/**
  * Represents the state of a new order.
  * <p>
  * @param items the items of the order, indexed by item identifier
  * @param customer the customer of the order
  */

    record NewOrderState (Map<String,OrderItem> items, Customer customer)
    implements State
    {

/**
  * Returns the order state as order details.
  * <p>
  * @return the order state as order details
  */

        public OrderDetails toOrderDetails ()
        {
// -- returns the order items inside a "dummy" allocation, since actual allocations don't
// -- exist yet for this order
            return new OrderDetails (
                Map.of (
                    "Not Allocated",
                    new Allocation (
                        "N/A",
                        "N/A",
                        new Address ("N/A", "N/A", "N/A", 0),
                        new HashMap<> (items ()),
                        "No Courier",
                        null,
                        Map.of (Instant.now (), Allocation.Status.CREATED))),
                customer);
        }

/**
  * Returns a new order state.
  * <p>
  * @param items the items of the order, indexed by item identifier
  * @param customer the customer of the order
  * @return the new order state
  */

        public static State newOrder (Map<String,OrderItem> items, Customer customer)
        {
            Map<String,OrderItem> newItems = new HashMap<> (items);

            return new NewOrderState (newItems, customer);
        }

    }

/**
  * Represents the state of an allocated order.
  * <p>
  * @param allocations the order allocations indexed by allocation identifier
  * @param customer the customer
  */

    record AllocatedOrderState (Map<String,Allocation> allocations, Customer customer)
    implements State
    {

/**
  * Returns the order state as order details.
  * <p>
  * @return the order state as order details
  */

        public OrderDetails toOrderDetails ()
        {
            return new OrderDetails (new HashMap<> (allocations ()), customer ());
        }

/**
  * Returns true if an allocation exists for the given identifier.
  * <p>
  * @param ident the order allocation identifier
  * @return true if an allocation exists for the given identifier
  */

        public boolean hasAllocation (String ident)
        {
            return allocations.containsKey (ident);
        }

/**
  * Returns the allocation matching the given identifier, or null if it doesn't exist.
  * <p>
  * @param ident the order allocation identifier
  * @return the allocation matching the given identifier, or null if it doesn't exist
  */

        public Allocation getAllocation (String ident)
        {
            return allocations.get (ident);
        }

/**
  * Returns the latest (most current) processing status of the requested allocation.
  * {@link Allocation.Status#NA} is returned if the requested allocation doesn't exist.
  * <p>
  * @param ident the order allocation identifier 
  * @return the latest (most current) processing status of the requested allocation
  */

        public Allocation.Status getLatestAllocationStatus (String ident)
        {
            Allocation.Status latest = Allocation.Status.NA;

            if (allocations.containsKey (ident)) {
                latest = allocations.get (ident).getLatestAllocationStatus ();
            }

            return latest;
        }

/**
  * Returns a new allocated order state.
  * <p>
  * @param allocations the order allocations indexed by allocation identifier
  * @param customer the customer
  * @return the new allocated order state
  */

        public static State allocatedOrder (Map<String,Allocation> allocations, Customer customer)
        {
            Map<String,Allocation> newAllocations = new HashMap<> ();

            if (allocations != null && !allocations.isEmpty ()) {
                newAllocations.putAll (allocations);
            }

            return new AllocatedOrderState (newAllocations, customer);
        }

/**
  * Returns a new allocated order state based on the provided allocations and customer,
  * with a new status + tracking identifier added to the requested allocation.
  * <p>
  * @param allocations the original allocations
  * @param customer the customer
  * @param allocationId the identifier of the allocation that should be re-created
  * @param trackingId the tracking identifier for the re-created allocation
  * @param timestamp the timestamp of the new status of the re-created allocation
  * @return the new allocated order state
  */

        public static State allocatedOrderWithNewTrackingId (
            Map<String,Allocation> allocations,
            Customer customer,
            String allocationId,
            String trackingId,
            Instant timestamp)
        {
            Map<String,Allocation> newAllocations = new HashMap<> ();

            if (allocations != null) {
                newAllocations =
                    allocations.values ()
                               .stream ()
                               .map (allocation -> {
                                   if (allocation.getID ().equals (allocationId)) {
                                       SortedMap<Instant, Allocation.Status>
                                           statuses = allocation.getStatuses ();
                                       statuses.put (timestamp, Allocation.Status.PACKED);
                                       return new Allocation (
                                           allocation.getID (),
                                           allocation.getName (),
                                           allocation.getAddress (),
                                           allocation.getItems (),
                                           allocation.getCourier (),
                                           trackingId,
                                           statuses);
                                   } else {
                                       return allocation;
                                   }
                               })
                               .collect (Collectors.toMap (Allocation::getID,
                                                           Function.identity ()));
            }

            return new AllocatedOrderState (newAllocations, customer);
        }

/**
  * Returns a new allocated order state based on the provided allocations and customer,
  * with a new status added to the requested allocation.
  * <p>
  * @param allocations the original allocations
  * @param customer the customer
  * @param allocationId the identifier of the allocation that should be re-created
  * @param status the new status of the re-created allocation
  * @param timestamp the timestamp of the new status of the re-created allocation
  * @return the new allocated order state
  */

        public static State allocatedOrderWithNewStatus (
            Map<String,Allocation> allocations,
            Customer customer,
            String allocationId,
            Allocation.Status status,
            Instant timestamp)
        {
            Map<String,Allocation> newAllocations = new HashMap<> ();

            if (allocations != null) {
                newAllocations =
                    allocations.values ()
                               .stream ()
                               .map (allocation -> {
                                   if (allocation.getID ().equals (allocationId)) {
                                       SortedMap<Instant, Allocation.Status>
                                           statuses = allocation.getStatuses ();
                                       statuses.put (timestamp, status);
                                       return new Allocation (
                                           allocation.getID (),
                                           allocation.getName (),
                                           allocation.getAddress (),
                                           allocation.getItems (),
                                           allocation.getCourier (),
                                           allocation.getTrackingId (),
                                           statuses);
                                   } else {
                                       return allocation;
                                   }
                               })
                               .collect (Collectors.toMap (Allocation::getID,
                                                           Function.identity ()));
            }

            return new AllocatedOrderState (newAllocations, customer);
        }

    }

/**
  * Holds the entity type key.
  */

    static final EntityTypeKey<Command>
        ENTITY_KEY = EntityTypeKey.create (Command.class, "Order");

/**
  * Holds the maximum number of booking API calls that be concurrently in progress.
  */

    private static final int MAX_BOOKINGS_IN_PROGRESS = 10;

/**
  * Holds the order identifier.
  */

    private final String _ident;

/**
  * Holds the actor context.
  */

    private final ActorContext<Command> _ctx;

/**
  * Used for executing the courier booking API call.
  */

    private final Http _http;

/**
  * Used for executing the courier booking API call.
  */

    private final Materializer _materializer;

/**
  * Holds the current number of booking API calls in progress.
  */

    private int _bookingsInProgress = 0;

/**
  * Initializes cluster sharding for Order entities, which distributes the entities over
  * the nodes in the Akka cluster.
  * <p>
  * @param system the actor system
  */

    public static void init (ActorSystem<?> system)
    {
        ClusterSharding.get (system).init (
            Entity.of (
                ENTITY_KEY,
                ctx -> Order.create (ctx.getEntityId ())));
    }

/**
  * Creates an actor for the given order identifier.
  * <p>
  * @param orderId the order identifier
  * @return a new actor for the given order identifier
  */

    public static Behavior<Command> create (String orderId)
    {
        return Behaviors.setup (
            ctx -> EventSourcedBehavior.start (new Order (orderId, ctx), ctx));
    }

    @Override
    public RetentionCriteria retentionCriteria ()
    {
        return RetentionCriteria.snapshotEvery (100);
    }

/**
  * Creates a new order instance.
  * <p>
  * @param orderId the order identifier.
  * @param ctx the actor context
  */

    private Order (String orderId, ActorContext<Command> ctx)
    {
        super (
            PersistenceId.of (ENTITY_KEY.name (), orderId),
            SupervisorStrategy.restartWithBackoff (Duration.ofMillis (200),
                                                   Duration.ofSeconds (5),
                                                   0.1));
        _ident = orderId;
        _ctx = ctx;
        _http = Http.get (ctx.getSystem ());
        _materializer = Materializer.createMaterializer (ctx.getSystem ());
    }

    @Override
    public State emptyState ()
    {
        return new BlankState ();
    }

// ------------------------------------------------------------
// Command handling logic
// ------------------------------------------------------------

/**
  * Returns a handler for incoming commands.
  * <p>
  * @return the command handler
  */

    @Override
    public CommandHandlerWithReply<Command, Event, State> commandHandler ()
    {
        return newOrderHandler ()
            .orElse (existingOrderHandler ())
            .orElse (allocatedOrderHandler ())
            .orElse (defaultHandler ())
            .build ();
    }

/**
  * Return command handler for new (empty) orders. Only {@link ReceiveOrder} is allowed.
  * <p>
  * @return the command handler
  */

    private CommandHandlerWithReplyBuilderByState<Command, Event, State, State>
        newOrderHandler ()
    {
        return newCommandHandlerWithReplyBuilder ()
            .forState (state -> state instanceof BlankState)
            .onCommand (ReceiveOrder.class, this::onReceiveOrder)
            .onCommand (
                PackOrderAllocation.class,
                cmd -> Effect ().reply (cmd.replyTo (),
                                        StatusReply.error ("Order doesn't exist")))
            .onCommand (
                UpdateTracking.class,
                cmd -> Effect ().reply (cmd.replyTo (),
                                        StatusReply.error ("Order doesn't exist")));
    }

/**
  * Return command handler for existing orders without allocations.
  * Only {@link ReceiveOrderAllocations} is allowed.
  * <p>
  * @return the command handler
  */

    private CommandHandlerWithReplyBuilderByState<Command, Event, State, State>
        existingOrderHandler ()
    {
        return newCommandHandlerWithReplyBuilder ()
            .forState (state -> state instanceof NewOrderState)
            .onCommand (
                ReceiveOrder.class,
                cmd -> Effect ().reply (cmd.replyTo (),
                                        StatusReply.error ("Order already exists")))
            .onCommand (ReceiveOrderAllocations.class, this::onReceiveAllocations)
            .onCommand (
                PackOrderAllocation.class,
                cmd -> Effect ().reply (cmd.replyTo (),
                                        StatusReply.error ("Order has no allocations")))
            .onCommand (
                UpdateTracking.class,
                cmd -> Effect ().reply (cmd.replyTo (),
                                        StatusReply.error ("Order has no allocations")));
    }

/**
  * Return command handler for existing orders with allocations.
  * Only {@link ReceiveOrderAllocations} is allowed.
  * <p>
  * @return the command handler
  */

    private CommandHandlerWithReplyBuilderByState<Command, Event, State, State>
        allocatedOrderHandler ()
    {
        return newCommandHandlerWithReplyBuilder ()
            .forState (state -> state instanceof AllocatedOrderState)
            .onCommand (
                ReceiveOrder.class,
                cmd -> Effect ().reply (cmd.replyTo (),
                                        StatusReply.error ("Order already exists")))
            .onCommand (
                ReceiveOrderAllocations.class,
                cmd -> Effect ().noReply ())
            .onCommand (PackOrderAllocation.class, this::onPackAllocation)
            .onCommand (WrappedPackOrderAllocationResult.class, this::onPackAllocationResult)
            .onCommand (UpdateTracking.class, this::onUpdateTracking);
    }

/**
  * Return default command handler. Only {@link FetchOrderDetails} is allowed.
  * <p>
  * @return the default command handler
  */

    private CommandHandlerWithReplyBuilderByState<Command, Event, State, State>
        defaultHandler ()
    {
        return newCommandHandlerWithReplyBuilder ()
            .forAnyState ()
            .onCommand (FetchOrderDetails.class,
                        (state, cmd) -> Effect ().reply (cmd.replyTo (),
                                                         state.toOrderDetails ()));
    }

/**
  * Handles a new order. The order is persisted, order allocation is initiated, and an
  * acknowledgement is sent to the caller.
  * <p>
  * @param state the order state
  * @param cmd the command
  * @return the reply effect
  */

    private ReplyEffect<Event, State> onReceiveOrder (State state, ReceiveOrder cmd)
    {
        if (cmd.customer () == null) {
            return Effect ().reply (cmd.replyTo (),
                                    StatusReply.error ("Customer details must be supplied"));
        } else if (cmd.items () == null || cmd.items ().isEmpty ()) {
            return Effect ().reply (cmd.replyTo (),
                                    StatusReply.error ("Order must contain at least one item"));
        } else {
            return Effect ().persist (new OrderReceived (_ident,
                                                         cmd.items (),
                                                         cmd.customer ()))
                            .thenRun (newState ->
                                _ctx.spawn (OrderAllocator.create (), _ident + "-allocator")
                                    .tell (new OrderAllocator.Allocate (cmd.items (),
                                                                        cmd.customer (),
                                                                        _ctx.getSelf ())))
                            .thenReply (cmd.replyTo (), x -> StatusReply.ack ());
        }
    }

/**
  * Handles received order allocations. The order allocations are persisted.
  * <p>
  * @param state the order state
  * @param cmd the command
  * @return the reply effect
  */

    private ReplyEffect<Event, State> onReceiveAllocations (State state, ReceiveOrderAllocations cmd)
    {
        if (cmd.allocations () != null && !cmd.allocations ().isEmpty ()) {
            return Effect ().persist (new OrderAllocationsReceived (_ident,
                                                                    cmd.allocations ()))
                            .thenNoReply ();
        } else {
            return Effect ().noReply ();
        }
    }

/**
  * Handles order allocation packing. The courier booking API is executed, and when a
  * response is returned, a new command containing the response is sent to same actor.
  * <p>
  * @param state the order state
  * @param cmd the command
  * @return the reply effect
  */

    private ReplyEffect<Event, State> onPackAllocation (State state, PackOrderAllocation cmd)
    {
        if (cmd.allocationId () == null || cmd.allocationId ().isBlank ()) {
            return Effect ().reply (cmd.replyTo (),
                                    StatusReply.error ("Pack items request must contain a valid allocation identifier"));
        } else if (state instanceof AllocatedOrderState allocated) {
            if (allocated.hasAllocation (cmd.allocationId ())) {
                if (allocated.getLatestAllocationStatus (cmd.allocationId ()) == Allocation.Status.ALLOCATED) {
                    if (_bookingsInProgress == MAX_BOOKINGS_IN_PROGRESS) {
                        return Effect ().reply (cmd.replyTo (),
                            StatusReply.error ("Max " + MAX_BOOKINGS_IN_PROGRESS + " concurrent pack operations supported"));
                    } else {
                        try {
                            Allocation allocation = allocated.getAllocation (cmd.allocationId ());
                            CourierBookingAPI booking =
                                CourierBookingAPI.getInstanceOrDefault (allocation.getCourier ());
                            CourierBookingHandler handler = booking.getBookingHandler();
                            Map<String,String> params = handler.getHTTPRequestParams (
                                _ident,
                                allocation,
                                allocated.customer ());
                            String uri = handler.getBookingURI (booking.getBaseURI (), params);
                            _bookingsInProgress++;
                            final CompletionStage<HttpResponse>
                                futureResponse = _http.singleRequest (HttpRequest.create (uri));
                            _ctx.pipeToSelf (
                                futureResponse,
                                (response, ex) -> {
                                    if (ex != null) {
// -- booking API failed
                                        return new WrappedPackOrderAllocationResult (
                                            cmd.allocationId (),
                                            new BookDeliveryFailure ("failed to book delivery: " + ex.getMessage ()),
                                            cmd.replyTo ());
                                    } else {
                                        if (response.status ().equals (StatusCodes.OK)) {
// -- booking API successful
                                            return
                                                response.entity ()
                                                        .toStrict (5000, _materializer)
                                                        .thenApply (entity -> {
                                                            try {
                                                                String responseBody = entity.getData ().utf8String ();
                                                                String trackingId = booking.getBookingHandler ().getTrackingId (responseBody);
                                                                return new WrappedPackOrderAllocationResult (
                                                                    cmd.allocationId (),
                                                                    new BookDeliverySuccess (trackingId),
                                                                    cmd.replyTo ());
                                                            }
                                                            catch (CourierBookingHandlerException bookingEx) {
                                                                return new WrappedPackOrderAllocationResult (
                                                                    cmd.allocationId (),
                                                                    new BookDeliveryFailure ("failed to book delivery: " + bookingEx.getMessage ()),
                                                                    cmd.replyTo ());
                                                            }
                                                        })
                                                        .toCompletableFuture ()
                                                        .join ();
                                        } else {
// -- booking API failed
                                            return new WrappedPackOrderAllocationResult (
                                                cmd.allocationId (),
                                                new BookDeliveryFailure ("failed to book delivery with status code " + response.status ()),
                                                cmd.replyTo ());
                                        }
                                    }
                                });
                            return Effect ().noReply ();
                        }
                        catch (CourierBookingHandlerException bookingEx) {
                            return Effect ().reply (cmd.replyTo (),
                                StatusReply.error ("failed to book delivery: " + bookingEx.getMessage ()));
                        }
                    }
                } else {
                    return Effect ().reply (
                        cmd.replyTo (),
                        StatusReply.success (
                            new PackOrderAllocationResult (
                                allocated.getAllocation (cmd.allocationId ()).getTrackingId ())));
                }
            } else {
                return Effect ().reply (cmd.replyTo (),
                                        StatusReply.error ("Allocation not found"));
            }
        } else {
            return Effect ().reply (cmd.replyTo (),
                                    StatusReply.error ("Order is not yet allocated"));
        }
    }

/**
  * Completes the handling of order allocation packing, when the courier booking API
  * response is returned. Persists the new allocation state.
  * <p>
  * @param state the order state
  * @param cmd the command
  * @return the reply effect
  */

    private ReplyEffect<Event, State> onPackAllocationResult (State state, WrappedPackOrderAllocationResult cmd)
    {
        _bookingsInProgress--;
        if (state instanceof AllocatedOrderState allocated) {
            if (allocated.hasAllocation (cmd.allocationId ())) {
                if (allocated.getLatestAllocationStatus (cmd.allocationId ()) == Allocation.Status.ALLOCATED) {
                    return switch (cmd.result) {
                    case BookDeliverySuccess success ->
                        Effect ().persist (new OrderAllocationPacked (_ident,
                                                                      cmd.allocationId (),
                                                                      success.trackingId (),
                                                                      Instant.now ()))
                                 .thenReply (
                                     cmd.replyTo (),
                                     newstate -> StatusReply.success (
                                         new PackOrderAllocationResult (success.trackingId ())));
                    case BookDeliveryFailure failure ->
                        Effect ().reply (
                            cmd.replyTo (),
                            StatusReply.error ("failed to book delivery: " + failure.reason));
                    };
                } else {
                    return Effect ().reply (
                        cmd.replyTo (),
                        StatusReply.success (
                            new PackOrderAllocationResult (
                                allocated.getAllocation (cmd.allocationId ()).getTrackingId ())));
                }
            } else {
                return Effect ().reply (cmd.replyTo (),
                                        StatusReply.error ("Allocation not found"));
            }
        } else {
            return Effect ().reply (cmd.replyTo (),
                                    StatusReply.error ("Order is not yet allocated"));
        }
    }

/**
  * Handles order allocation tracking update. Persists the new allocation state.
  * <p>
  * @param state the order state
  * @param cmd the command
  * @return the reply effect
  */

    private ReplyEffect<Event, State> onUpdateTracking (State state, UpdateTracking cmd)
    {
        if (cmd.allocationId () == null || cmd.allocationId ().isBlank ()) {
            return Effect ().reply (cmd.replyTo (),
                                    StatusReply.error ("Tracking update request must contain a valid allocation identifier"));
        } else if (cmd.status () == null) {
            return Effect ().reply (cmd.replyTo (),
                                    StatusReply.error ("Tracking update request must contain a valid status"));
        } else if (state instanceof AllocatedOrderState allocated) {
            if (allocated.hasAllocation (cmd.allocationId ())) {
                if (allocated.getLatestAllocationStatus (cmd.allocationId ()).ordinal () >= Allocation.Status.PACKED.ordinal () &&
                    allocated.getLatestAllocationStatus (cmd.allocationId ()).ordinal () < cmd.status().ordinal ()) {
                    return Effect ().persist (new TrackingUpdated (_ident,
                                                                   cmd.allocationId (),
                                                                   cmd.status (),
                                                                   Instant.now ()))
                                    .thenReply (cmd.replyTo (), x -> StatusReply.ack ());
                } else {
                    return Effect ().reply (
                        cmd.replyTo (),
                        StatusReply.error ("Existing allocation status inconsistent with requested tracking status"));
                }
            } else {
                return Effect ().reply (
                    cmd.replyTo (),
                    StatusReply.error ("Allocation not found"));
            }
        } else {
            return Effect ().reply (
                cmd.replyTo (),
                StatusReply.error ("Order is not yet allocated"));
        }
    }

// ------------------------------------------------------------
// Event handling logic
// ------------------------------------------------------------

/**
  * Handle events.
  */

    @Override
    public EventHandler<State, Event> eventHandler () {
        var builder = newEventHandlerBuilder ();
        builder
            .forStateType (BlankState.class)
            .onEvent (OrderReceived.class, (state, evt) -> Order.NewOrderState.newOrder (evt.items (), evt.customer ()));

        builder
            .forStateType (NewOrderState.class)
            .onEvent (OrderAllocationsReceived.class, (state, evt) -> Order.AllocatedOrderState.allocatedOrder (evt.allocations (), state.customer ()));

        builder
            .forStateType (AllocatedOrderState.class)
            .onEvent(OrderAllocationPacked.class, (state, evt) -> Order.AllocatedOrderState.allocatedOrderWithNewTrackingId (state.allocations (), state.customer (), evt.allocationId (), evt.trackingId (), evt.timestamp ()))
            .onEvent(TrackingUpdated.class, (state, evt) -> Order.AllocatedOrderState.allocatedOrderWithNewStatus (state.allocations (), state.customer (), evt.allocationId (), evt.status (), evt.timestamp ()));

        return builder.build ();
    }

/**
  * Handle recovery.
  */

    @Override
    public SignalHandler<State> signalHandler ()
    {
        return newSignalHandlerBuilder ()
            .onSignal (
                RecoveryCompleted.instance (),
                state -> {
// -- if a new order was persisted, but failed to be assigned allocations, attempt to
// -- create allocations again
                    if (state instanceof NewOrderState newOrder) {
                        _ctx.spawn (OrderAllocator.create (), _ident + "-allocator")
                            .tell (new OrderAllocator.Allocate (newOrder.items (),
                                                                newOrder.customer (),
                                                                _ctx.getSelf ()));
                    }
                })
            .build ();
    }

}
