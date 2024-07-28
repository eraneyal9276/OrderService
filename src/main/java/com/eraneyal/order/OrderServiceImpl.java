package com.eraneyal.order;

import com.eraneyal.order.proto.OrderDetailsRequest;
import com.eraneyal.order.proto.OrderDetailsResponse;
import com.eraneyal.order.proto.ReceiveOrderRequest;
import com.eraneyal.order.proto.ReceiveOrderResponse;
import com.eraneyal.order.proto.TrackUpdateRequest;
import com.eraneyal.order.proto.TrackUpdateResponse;
import com.eraneyal.order.proto.TrackingStatus;
import com.eraneyal.order.proto.OrderService;
import com.eraneyal.order.proto.PackItemsRequest;
import com.eraneyal.order.proto.PackItemsResponse;

import akka.Done;
import akka.actor.typed.ActorSystem;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import akka.grpc.GrpcServiceException;
import io.grpc.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
  * Implements order services.
  */

public final class OrderServiceImpl implements OrderService
{

	private final Logger logger = LoggerFactory.getLogger (getClass ());

	private final Duration timeout;

	private final ClusterSharding sharding;

/**
  * Creates a new order service instance.
  */

	public OrderServiceImpl (ActorSystem<?> system) {
		timeout = system.settings ().config ().getDuration ("order-service.ask-timeout");
		sharding = ClusterSharding.get (system);
	}

/**
  * Implements the receive order service.
  */

	@Override
	public CompletionStage<ReceiveOrderResponse> receiveOrder (ReceiveOrderRequest in) {
// -- validate input
		if (in.getOrderId () == null || in.getOrderId().isBlank ()) {
			throw new GrpcServiceException (
				Status.INVALID_ARGUMENT.withDescription ("Missing order identifier"));
		}
		Map<String,OrderItem> items = getItems (in);
		Customer customer = getCustomer (in);

		logger.info("receiveOrder {}", in.getOrderId ());
		EntityRef<Order.Command>
		    entityRef = sharding.entityRefFor (Order.ENTITY_KEY, in.getOrderId ());

		CompletionStage<Done> reply =
			entityRef.askWithStatus (replyTo -> new Order.ReceiveOrder (items, customer, replyTo), timeout);

		CompletionStage<ReceiveOrderResponse> response =
			reply.thenApply (done -> ReceiveOrderResponse.newBuilder ().setOk (true).build ());

		return convertError (response);
	}

/**
  * Implements the pack items service.
  */

	@Override
	public CompletionStage<PackItemsResponse> packItems (PackItemsRequest in) {
// -- validate input
		if (in.getOrderId () == null || in.getOrderId ().isBlank ()) {
			throw new GrpcServiceException (
				Status.INVALID_ARGUMENT.withDescription ("Missing order identifier"));
		}

		logger.info("receivePackItems {}-{}", in.getOrderId (), in.getAllocationId ());
		EntityRef<Order.Command>
		    entityRef = sharding.entityRefFor (Order.ENTITY_KEY, in.getOrderId ());

		CompletionStage<Order.PackOrderAllocationResult> reply =
			entityRef.askWithStatus (replyTo ->
				new Order.PackOrderAllocation (in.getAllocationId (), replyTo), timeout);
		CompletionStage<PackItemsResponse> response =
			reply.thenApply (
				result -> PackItemsResponse.newBuilder ()
										   .setTrackingId (result.trackingId ())
										   .build ());

		return convertError (response);
	}

/**
  * Implements the tracking update service.
  */

	@Override
	public CompletionStage<TrackUpdateResponse> trackingUpdate (TrackUpdateRequest in) {
// -- validate input
		if (in.getOrderId () == null || in.getOrderId ().isBlank ()) {
			throw new GrpcServiceException (
				Status.INVALID_ARGUMENT.withDescription ("Missing order identifier"));
		}

		logger.info("trackUpdate {}-{}-{}", in.getOrderId (), in.getAllocationId (), in.getStatus ());
		EntityRef<Order.Command>
		    entityRef = sharding.entityRefFor (Order.ENTITY_KEY, in.getOrderId ());

		CompletionStage<Done> reply =
			entityRef.askWithStatus (replyTo ->
				new Order.UpdateTracking (in.getAllocationId (),
										  toStatus (in.getStatus ()),
										  replyTo),
				timeout);
		CompletionStage<TrackUpdateResponse> response =
			reply.thenApply (done -> TrackUpdateResponse.newBuilder ().setOk (true).build ());

		return convertError (response);
	}

/**
  * Implements the fetch order details service.
  */

	@Override
	public CompletionStage<OrderDetailsResponse> fetchOrderDetails (OrderDetailsRequest in) {
// -- validate input
		if (in.getOrderId () == null || in.getOrderId ().isBlank ()) {
			throw new GrpcServiceException (
				Status.INVALID_ARGUMENT.withDescription ("Missing order identifier"));
		}

		logger.info("fetchOrderDetails {}", in.getOrderId ());
		EntityRef<Order.Command>
	    	entityRef = sharding.entityRefFor (Order.ENTITY_KEY, in.getOrderId ());

		CompletionStage<Order.OrderDetails> reply =
			entityRef.ask (Order.FetchOrderDetails::new, timeout);

		CompletionStage<OrderDetailsResponse> protoOrder =
			reply.thenApply (
				order -> {
					if (order.allocations ().isEmpty ())
						throw new GrpcServiceException (
							Status.NOT_FOUND.withDescription ("Order " + in.getOrderId () + " not found"));
					else return toProtoOrder (in.getOrderId (), order);
				});

		return convertError (protoOrder);
	}

/**
  * Validates the request proto Items and converts them to order Items.
  * <p>
  * @param request the proto request
  * @return the validated order Items
  */

	private static Map<String,OrderItem> getItems (ReceiveOrderRequest request)
	{
		Map<String,OrderItem> items = new HashMap<> ();

		try {
			for (int i = 0; i < request.getItemsCount (); i++) {
				com.eraneyal.order.proto.Item item = request.getItems (i);
				items.put (item.getItemId (),
						   new OrderItem (item.getItemId (), item.getName (), item.getQuantity ()));
			}
		}
		catch (Exception exc) {
			throw new GrpcServiceException (
				Status.INVALID_ARGUMENT.withDescription (exc.getMessage ()));
		}

		return items;
	}

/**
  * Validates the request proto Customer and converts it to Customer.
  * <p>
  * @param request the proto request
  * @return the validated Customer
  */

	private static Customer getCustomer (ReceiveOrderRequest request)
	{
		Customer customer = null;

		try {
			com.eraneyal.order.proto.Customer protoCustomer = request.getCustomer ();
			com.eraneyal.order.proto.Address protoAddress = protoCustomer.getAddress ();
			customer = new Customer (
				protoCustomer.getFirstName (),
				protoCustomer.getLastName (),
				new Address (
					protoAddress.getStreet (),
					protoAddress.getCity (),
					protoAddress.getCountry (),
					protoAddress.getZipCode ()),
				protoCustomer.getEmail (),
				protoCustomer.getMobilePhone ());
		}
		catch (Exception exc) {
			throw new GrpcServiceException (
				Status.INVALID_ARGUMENT.withDescription (exc.getMessage ()));
		}

		return customer;
	}

/**
  * Translates TrackingStatus to Allocation.Status.
  */

	private static Allocation.Status toStatus (TrackingStatus protoStatus)
	{
		return switch (protoStatus) {
		case TrackingStatus.PICKED_BY_COURIER -> Allocation.Status.PICKED_BY_COURIER;
		case TrackingStatus.ENROUTE_TO_CUSTOMER -> Allocation.Status.ENROUTE_TO_CUSTOMER;
		case TrackingStatus.DELIVERED -> Allocation.Status.DELIVERED;
		default -> Allocation.Status.NA;
		};
	}

/**
  * Converts a map of order Items to a List of proto Items.
  * <p>
  * @param items the map of items
  * @return the list of proto items
  */

	private static List<com.eraneyal.order.proto.Item> toProtoItems (Map<String,OrderItem> items)
	{
		return items.values ()
				    .stream ()
				    .map (item ->
				    	com.eraneyal.order.proto.Item.newBuilder ()
				    								 .setItemId (item.getItemID ())
						   			   				 .setName (item.getName ())
						   			   				 .setQuantity (item.getQuantity ())
						   			   				 .build ())
				   .collect (Collectors.toList ());
	}

/**
  * Converts an Address to a proto Address.
  * <p>
  * @param address the Address
  * @return the proto Address
 */

	private static com.eraneyal.order.proto.Address toProtoAddress (Address address)
	{
		return com.eraneyal.order.proto.Address.newBuilder ()
					  						   .setStreet (address.getStreet ())
					  						   .setCity (address.getCity ())
					  						   .setCountry (address.getCountry ())
					  						   .setZipCode (address.getZipCode ())
					  						   .build ();
	}

/**
  * Converts a map of allocation Statuses to a List of proto statuses.
  * <p>
  * @param statuses the map of allocation statuses indexed by timestamp
  * @return the list of proto statuses
  */

	private static List<com.eraneyal.order.proto.Status> toProtoStatuses (Map<Instant,Allocation.Status> statuses)
	{
		return statuses.entrySet ()
					   .stream ()
					   .map (entry ->
					       com.eraneyal.order.proto.Status.newBuilder ()
							   							  .setTimestamp (entry.getKey ().toString ())
							   							  .setType (entry.getValue ().toString ())
							   							  .build ())
					   .collect(Collectors.toList ());
	}

/**
  * Converts order details to proto Order.
  * <p>
  * @param orderId the order identifier
  * @param order the order details
  * @return the proto order
  */

	private static OrderDetailsResponse toProtoOrder (String orderId, Order.OrderDetails order)
	{
		List<com.eraneyal.order.proto.Allocation> protoAllocations =
			order.allocations ()
				 .values ()
				 .stream ()
				 .map (alloc ->
				     com.eraneyal.order.proto.Allocation.newBuilder ()
						 				 				.setAllocationId (alloc.getID ())
						 				 				.setName(alloc.getName ())
						 				 				.setAddress (toProtoAddress (alloc.getAddress ()))
						 				 				.addAllItems (toProtoItems (alloc.getItems ()))
						 				 				.setCourier (alloc.getCourier ())
						 				 				.setTrackingId (alloc.getTrackingId () != null ? alloc.getTrackingId () : "")
						 				 				.addAllStatuses (toProtoStatuses (alloc.getStatuses ()))
						 				 				.build ())
				 .collect(Collectors.toList());
		Customer customer = order.customer ();
		return OrderDetailsResponse.newBuilder ()
								   .setOrderId (orderId)
								   .addAllAllocations (protoAllocations)
								   .setCustomer (
								       com.eraneyal.order.proto.Customer.newBuilder ()
										   		   .setFirstName (customer.getFirstName ())
										   		   .setLastName (customer.getLastName ())
										   		   .setAddress (toProtoAddress (customer.getAddress ()))
										   		   .setEmail (customer.getEMail ())
										   		   .setMobilePhone (customer.getMobilePhone ())
										   		   .build ())
								   .build ();
	}

/**
  * Converts errors returned by the order entity.
  */

	private static <T> CompletionStage<T> convertError (CompletionStage<T> response) {
		return response.exceptionally (
			exc -> {
				if (exc instanceof TimeoutException) {
					throw new GrpcServiceException (
						Status.UNAVAILABLE.withDescription ("Operation timed out"));
				} else {
					throw new GrpcServiceException (
						Status.INVALID_ARGUMENT.withDescription (exc.getMessage ()));
				}
			});
	}

}