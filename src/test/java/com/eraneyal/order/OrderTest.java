package com.eraneyal.order;

import akka.Done;
import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.pattern.StatusReply;
import akka.persistence.testkit.javadsl.EventSourcedBehaviorTestKit;
import akka.persistence.testkit.javadsl.EventSourcedBehaviorTestKit.CommandResultWithReply;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.time.Instant;
import java.util.Map;

/**
  * Unit tests for Order entity.
  */

public class OrderTest
{

/**
  * Holds a test order identifier.
  */

    private static final String ORDER_ID = "testOrder";

/**
  * Holds test items.
  */

    private static final Map<String,OrderItem> ITEMS = Map.of (
        "1",
        new OrderItem ("1", "pencil", 5),
        "2",
        new OrderItem ("2", "pen", 5),
        "3",
        new OrderItem ("3", "notebook", 3),
        "4",
        new OrderItem ("4", "folder", 2));

/**
  * Holds a test customer.
  */

    private static final Customer CUSTOMER = new Customer (
        "Eran",
        "Eyal",
        new Address (
            "Some Street 42",
            "Some City",
            "Israel",
            12345),
        "someone@gmail.com",
        "0521234567");

    @ClassRule
    public static final TestKitJunitResource testKit =
        new TestKitJunitResource (EventSourcedBehaviorTestKit.config ());

    private EventSourcedBehaviorTestKit<Order.Command, Order.Event, Order.State>
        eventSourcedTestKit =
            EventSourcedBehaviorTestKit.create (testKit.system (), Order.create (OrderTest.ORDER_ID));

    @Before
    public void beforeEach ()
    {
        eventSourcedTestKit.clear ();
    }


/**
  * Tests receiving of a new order.
  */

    @Test
    public void receiveNewOrder ()
    {
        CommandResultWithReply<Order.Command,Order.Event,Order.State,StatusReply<Done>>
            result =
                eventSourcedTestKit.runCommand (
                    replyTo -> new Order.ReceiveOrder (
                        OrderTest.ITEMS,
                        OrderTest.CUSTOMER,
                        replyTo));
        assertTrue (result.reply ().isSuccess ());
        Done done = result.reply ().getValue ();
        assertEquals (new Order.OrderReceived (OrderTest.ORDER_ID,
                                               OrderTest.ITEMS,
                                               OrderTest.CUSTOMER),
                      result.event ());
    }

/**
  * Tests rejection of an existing order.
  */

    @Test
    public void rejectExistingOrder ()
    {
// -- receive a new order - succeeds
        CommandResultWithReply<Order.Command,Order.Event,Order.State,StatusReply<Done>>
            result =
                eventSourcedTestKit.runCommand (
                    replyTo -> new Order.ReceiveOrder (
                        OrderTest.ITEMS,
                        OrderTest.CUSTOMER,
                        replyTo));
        assertTrue (result.reply ().isSuccess ());
        Done done = result.reply ().getValue ();
        assertEquals (new Order.OrderReceived (OrderTest.ORDER_ID,
                                               OrderTest.ITEMS,
                                               OrderTest.CUSTOMER),
                      result.event ());
// -- receive an existing order - fails
        result =
            eventSourcedTestKit.runCommand (
                replyTo -> new Order.ReceiveOrder (
                    OrderTest.ITEMS,
                    OrderTest.CUSTOMER,
                    replyTo));
        assertTrue (result.reply ().isError ());
        assertTrue (result.hasNoEvents ());
    }

/**
  * Tests that all the items of a new offer are assigned to an allocation.
  */

    @Test
    public void checkAllocations ()
    {
// -- receive a new order
        CommandResultWithReply<Order.Command,Order.Event,Order.State,StatusReply<Done>>
            result1 =
                eventSourcedTestKit.runCommand (
                    replyTo -> new Order.ReceiveOrder (
                        OrderTest.ITEMS,
                        OrderTest.CUSTOMER,
                        replyTo));
        assertTrue (result1.reply ().isSuccess ());
        Done done = result1.reply ().getValue ();
        assertEquals (new Order.OrderReceived (OrderTest.ORDER_ID,
                                               OrderTest.ITEMS,
                                               OrderTest.CUSTOMER),
                      result1.event ());
// -- fetch order details in order to check the allocations
        CommandResultWithReply<Order.Command,Order.Event,Order.State,Order.OrderDetails>
            result2 =
                eventSourcedTestKit.runCommand (
                    replyTo -> new Order.FetchOrderDetails (replyTo));
        assertTrue (result2.reply ().allocations ().size () > 0);
        int totalItems = 0;
        for (Allocation allocation : result2.reply ().allocations ().values ()) {
            totalItems+= allocation.getItems().size ();
        }
        assertEquals (OrderTest.ITEMS.size (), totalItems);
    }

/**
  * Tests fetch order details for non existing order.
  */

    @Test
   	public void rejectFetchNonExistingOrder ()
   	{
        CommandResultWithReply<Order.Command,Order.Event,Order.State,Order.OrderDetails>
            result =
                eventSourcedTestKit.runCommand (
                    replyTo -> new Order.FetchOrderDetails (replyTo));
        assertTrue (result.reply ().allocations ().size () == 0);
   	}

/**
  * Tests packing of an order allocation (and booking delivery).
  */

    @Test
    public void packOrderAllocation ()
    {
// -- receive a new order
        CommandResultWithReply<Order.Command,Order.Event,Order.State,StatusReply<Done>>
            result1 =
                eventSourcedTestKit.runCommand (
                    replyTo -> new Order.ReceiveOrder (
                        OrderTest.ITEMS,
                        OrderTest.CUSTOMER,
                        replyTo));
        assertTrue (result1.reply ().isSuccess ());
        Done done = result1.reply ().getValue ();
        assertEquals (new Order.OrderReceived (OrderTest.ORDER_ID,
                                               OrderTest.ITEMS,
                                               OrderTest.CUSTOMER),
                      result1.event ());
// -- fetch order details in order to obtain the allocations
        CommandResultWithReply<Order.Command,Order.Event,Order.State,Order.OrderDetails>
            result2 =
                eventSourcedTestKit.runCommand (
                    replyTo -> new Order.FetchOrderDetails (replyTo));
        assertTrue (result2.reply ().allocations ().size () > 0);
// -- get first allocation
        String allocationID = result2.reply ().allocations ().keySet ().iterator ().next ();
// -- allocation has no tracking identifier yet
        assertTrue (result2.reply ().getAllocation (allocationID).getTrackingID () == null);
        assertEquals (Allocation.Status.ALLOCATED,
                      result2.reply ().getAllocation (allocationID).getLatestAllocationStatus ());
// -- pack items
        CommandResultWithReply<Order.Command,Order.Event,Order.State,StatusReply<Order.PackOrderAllocationResult>>
            result3 =
                eventSourcedTestKit.runCommand (
                    replyTo -> new Order.PackOrderAllocation (allocationID, replyTo));
        assertTrue (result3.reply ().isSuccess ());
        Order.PackOrderAllocationResult packed = result3.reply ().getValue ();
        assertTrue (result3.event () instanceof Order.OrderAllocationPacked);
        Order.OrderAllocationPacked packedEvent = (Order.OrderAllocationPacked) result3.event ();
        String trackingID = packedEvent.trackingId ();
        assertTrue (result3.state () instanceof Order.AllocatedOrderState);
        Order.AllocatedOrderState state = (Order.AllocatedOrderState) result3.state ();
        assertEquals (Allocation.Status.PACKED,
                      state.getLatestAllocationStatus (allocationID));
        assertEquals (trackingID,
                      state.getAllocation (allocationID).getTrackingID ());
        assertEquals (trackingID,
                      packed.trackingId ());
    }

/**
  * Tests rejection of pack items for non existing order.
  */

    @Test
    public void rejectPackForNonExistingOrder ()
    {
        CommandResultWithReply<Order.Command,Order.Event,Order.State,StatusReply<Order.PackOrderAllocationResult>>
            result =
                eventSourcedTestKit.runCommand (
                    replyTo -> new Order.PackOrderAllocation ("dummyAllocationId", replyTo));
        assertTrue (result.reply ().isError ());
        assertTrue (result.hasNoEvents ());
    }

/**
  * Tests rejection of pack items for non existing allocation.
  */

    @Test
    public void rejectPackForNonExistingAllocation ()
    {
// -- receive a new order
        CommandResultWithReply<Order.Command,Order.Event,Order.State,StatusReply<Done>>
            result1 =
                eventSourcedTestKit.runCommand (
                    replyTo -> new Order.ReceiveOrder (
                        OrderTest.ITEMS,
                        OrderTest.CUSTOMER,
                        replyTo));
        assertTrue (result1.reply ().isSuccess ());
        Done done = result1.reply ().getValue ();
        assertEquals (new Order.OrderReceived (OrderTest.ORDER_ID,
                                               OrderTest.ITEMS,
                                               OrderTest.CUSTOMER),
                      result1.event ());
// -- pack items
        CommandResultWithReply<Order.Command,Order.Event,Order.State,StatusReply<Order.PackOrderAllocationResult>>
            result2 =
                eventSourcedTestKit.runCommand (
                    replyTo -> new Order.PackOrderAllocation ("10", replyTo)); // this allocation ID shouldn't exist
        assertTrue (result2.reply ().isError ());
        assertTrue (result2.hasNoEvents ());
    }

/**
  * Tests pack items for an already packed allocation - the second call doesn't fail -
  * it simply returns the same tracking identifier.
  */

    @Test
    public void alreadyPackedAllocation ()
    {
// -- receive a new order
        CommandResultWithReply<Order.Command,Order.Event,Order.State,StatusReply<Done>>
            result1 =
                eventSourcedTestKit.runCommand (
                    replyTo -> new Order.ReceiveOrder (
                        OrderTest.ITEMS,
                        OrderTest.CUSTOMER,
                        replyTo));
        assertTrue (result1.reply ().isSuccess ());
        Done done = result1.reply ().getValue ();
        assertEquals (new Order.OrderReceived (OrderTest.ORDER_ID,
                                               OrderTest.ITEMS,
                                               OrderTest.CUSTOMER),
                      result1.event ());
// -- first pack items
        CommandResultWithReply<Order.Command,Order.Event,Order.State,StatusReply<Order.PackOrderAllocationResult>>
            result2 =
                eventSourcedTestKit.runCommand (
                    replyTo -> new Order.PackOrderAllocation ("1", replyTo)); // this allocation ID should exist
        assertTrue (result2.reply ().isSuccess ());
        assertFalse (result2.hasNoEvents ());
// -- second pack items
        CommandResultWithReply<Order.Command,Order.Event,Order.State,StatusReply<Order.PackOrderAllocationResult>>
            result3 =
                eventSourcedTestKit.runCommand (
                    replyTo -> new Order.PackOrderAllocation ("1", replyTo)); // this allocation ID should exist
        assertTrue (result3.reply ().isSuccess ());
        assertTrue (result3.hasNoEvents ());
        assertEquals (
            result2.reply ().getValue ().trackingId (),
            result3.reply ().getValue ().trackingId ());
    }

/**
  * Tests tracking updates.
  */

    @Test
    public void trackUpdate ()
    {
// -- receive a new order
        CommandResultWithReply<Order.Command,Order.Event,Order.State,StatusReply<Done>>
            result1 =
                eventSourcedTestKit.runCommand (
                    replyTo -> new Order.ReceiveOrder (
                        OrderTest.ITEMS,
                        OrderTest.CUSTOMER,
                        replyTo));
        assertTrue (result1.reply ().isSuccess ());
        Done done = result1.reply ().getValue ();
        assertEquals (new Order.OrderReceived (OrderTest.ORDER_ID,
                                               OrderTest.ITEMS,
                                               OrderTest.CUSTOMER),
                      result1.event ());
// -- fetch order details
        CommandResultWithReply<Order.Command,Order.Event,Order.State,Order.OrderDetails>
            result2 =
                eventSourcedTestKit.runCommand (
                    replyTo -> new Order.FetchOrderDetails (replyTo));
        assertTrue (result2.reply ().allocations ().size () > 0);
// -- obtain allocation identifier
        String allocationID = result2.reply ().allocations ().keySet ().iterator ().next ();
        assertTrue (result2.reply ().getAllocation (allocationID).getTrackingID () == null);
// -- pack items
        CommandResultWithReply<Order.Command,Order.Event,Order.State,StatusReply<Order.PackOrderAllocationResult>>
            result3 =
                eventSourcedTestKit.runCommand (
                    replyTo -> new Order.PackOrderAllocation (allocationID, replyTo));
        assertTrue (result3.reply ().isSuccess ());
        Order.PackOrderAllocationResult packed = result3.reply ().getValue ();
// -- update tracking to PICKED_BY_COURIER
        CommandResultWithReply<Order.Command,Order.Event,Order.State,StatusReply<Done>>
            result4 =
                eventSourcedTestKit.runCommand (
                    replyTo -> new Order.UpdateTracking (allocationID, Allocation.Status.PICKED_BY_COURIER, replyTo));
        assertTrue (result4.reply ().isSuccess ());
        done = result4.reply ().getValue ();
        Instant timestamp = Instant.now ();
// -- we must extract the timestamp from the actual event, otherwise it won't match the test event
        if (result4.event () instanceof Order.TrackingUpdated trackingEvent) {
            timestamp = trackingEvent.timestamp ();
        }
        assertEquals (new Order.TrackingUpdated (OrderTest.ORDER_ID,
                                                 allocationID,
                                                 Allocation.Status.PICKED_BY_COURIER,
                                                 timestamp),
                      result4.event ());
// -- update tracking to ENROUTE_TO_CUSTOMER
        CommandResultWithReply<Order.Command,Order.Event,Order.State,StatusReply<Done>>
            result5 =
                eventSourcedTestKit.runCommand (
                    replyTo -> new Order.UpdateTracking (allocationID, Allocation.Status.ENROUTE_TO_CUSTOMER, replyTo));
        assertTrue (result5.reply ().isSuccess ());
        done = result5.reply ().getValue ();
        timestamp = Instant.now ();
// -- we must extract the timestamp from the actual event, otherwise it won't match the test event
        if (result5.event () instanceof Order.TrackingUpdated trackingEvent) {
            timestamp = trackingEvent.timestamp ();
        }
        assertEquals (new Order.TrackingUpdated (OrderTest.ORDER_ID,
                                                 allocationID,
                                                 Allocation.Status.ENROUTE_TO_CUSTOMER,
                                                 timestamp),
                      result5.event ());
// -- update tracking to DELIVERED
        CommandResultWithReply<Order.Command,Order.Event,Order.State,StatusReply<Done>>
            result6 =
                eventSourcedTestKit.runCommand (
                    replyTo -> new Order.UpdateTracking (allocationID, Allocation.Status.DELIVERED, replyTo));
        assertTrue (result6.reply ().isSuccess ());
        done = result6.reply ().getValue ();
        timestamp = Instant.now ();
//-- we must extract the timestamp from the actual event, otherwise it won't match the test event
        if (result6.event () instanceof Order.TrackingUpdated trackingEvent) {
            timestamp = trackingEvent.timestamp ();
        }
        assertEquals (new Order.TrackingUpdated (OrderTest.ORDER_ID,
                                                 allocationID,
                                                 Allocation.Status.DELIVERED,
                                                 timestamp),
                      result6.event ());
    }

/**
  * Tests rejection of tracking update for non existing order.
  */

    @Test
    public void rejectTrackingUpdateForNonExistingOrder ()
    {
        CommandResultWithReply<Order.Command,Order.Event,Order.State,StatusReply<Done>>
            result =
                eventSourcedTestKit.runCommand (
                    replyTo -> new Order.UpdateTracking ("dummyAllocationId", Allocation.Status.DELIVERED, replyTo));
        assertTrue (result.reply ().isError ());
        assertTrue (result.hasNoEvents ());
    }

/**
  * Tests rejection of tracking update for non existing allocation.
  */

    @Test
    public void rejectTrackingUpdateForNonExistingAllocation ()
    {
// -- receive a new order
        CommandResultWithReply<Order.Command,Order.Event,Order.State,StatusReply<Done>>
            result1 =
                eventSourcedTestKit.runCommand (
                    replyTo -> new Order.ReceiveOrder (
                        OrderTest.ITEMS,
                        OrderTest.CUSTOMER,
                        replyTo));
        assertTrue (result1.reply ().isSuccess ());
        Done done = result1.reply ().getValue ();
        assertEquals (new Order.OrderReceived (OrderTest.ORDER_ID,
                                               OrderTest.ITEMS,
                                               OrderTest.CUSTOMER),
                      result1.event ());
// -- request update tracking for a non existing allocation
        CommandResultWithReply<Order.Command,Order.Event,Order.State,StatusReply<Done>>
            result2 =
                eventSourcedTestKit.runCommand (
                    replyTo -> new Order.UpdateTracking ("10", Allocation.Status.DELIVERED, replyTo)); // this allocation ID shouldn't exist
        assertTrue (result2.reply ().isError ());
        assertTrue (result2.hasNoEvents ());
    }

/**
  * Tests rejection of tracking update for inconsistent status.
  */

    @Test
    public void rejectTrackUpdateInconsistentStatus ()
    {
// -- receive a new order
        CommandResultWithReply<Order.Command,Order.Event,Order.State,StatusReply<Done>>
            result1 =
                eventSourcedTestKit.runCommand (
                    replyTo -> new Order.ReceiveOrder (
                        OrderTest.ITEMS,
                        OrderTest.CUSTOMER,
                        replyTo));
        assertTrue (result1.reply ().isSuccess ());
        Done done = result1.reply ().getValue ();
        assertEquals (new Order.OrderReceived (OrderTest.ORDER_ID,
                                               OrderTest.ITEMS,
                                               OrderTest.CUSTOMER),
                      result1.event ());
// -- fetch order details
        CommandResultWithReply<Order.Command,Order.Event,Order.State,Order.OrderDetails>
            result2 =
                eventSourcedTestKit.runCommand (
                    replyTo -> new Order.FetchOrderDetails (replyTo));
        assertTrue (result2.reply ().allocations ().size () > 0);
// -- obtain allocation identifier
        String allocationID = result2.reply ().allocations ().keySet ().iterator ().next ();
        assertTrue (result2.reply ().getAllocation (allocationID).getTrackingID () == null);
// -- request update tracking - should be rejected (allocation not packed yet)
        CommandResultWithReply<Order.Command,Order.Event,Order.State,StatusReply<Done>>
            result3 =
                eventSourcedTestKit.runCommand (
                    replyTo -> new Order.UpdateTracking (allocationID, Allocation.Status.PICKED_BY_COURIER, replyTo));
        assertTrue (result3.reply ().isError ());
        assertTrue (result3.hasNoEvents ());
// -- pack items
        CommandResultWithReply<Order.Command,Order.Event,Order.State,StatusReply<Order.PackOrderAllocationResult>>
            result4 =
                eventSourcedTestKit.runCommand (
                    replyTo -> new Order.PackOrderAllocation (allocationID, replyTo));
        assertTrue (result4.reply ().isSuccess ());
        Order.PackOrderAllocationResult packed = result4.reply ().getValue ();
// -- update tracking to PICKED_BY_COURIER
        CommandResultWithReply<Order.Command,Order.Event,Order.State,StatusReply<Done>>
            result5 =
                eventSourcedTestKit.runCommand (
                    replyTo -> new Order.UpdateTracking (allocationID, Allocation.Status.PICKED_BY_COURIER, replyTo));
        assertTrue (result5.reply ().isSuccess ());
        done = result5.reply ().getValue ();
        Instant timestamp = Instant.now ();
// -- we must extract the timestamp from the actual event, otherwise it won't match the test event
        if (result5.event () instanceof Order.TrackingUpdated trackingEvent) {
            timestamp = trackingEvent.timestamp ();
        }
        assertEquals (new Order.TrackingUpdated (OrderTest.ORDER_ID,
                                                 allocationID,
                                                 Allocation.Status.PICKED_BY_COURIER,
                                                 timestamp),
                      result5.event ());
// -- update tracking to ENROUTE_TO_CUSTOMER
        CommandResultWithReply<Order.Command,Order.Event,Order.State,StatusReply<Done>>
            result6 =
                eventSourcedTestKit.runCommand (
                    replyTo -> new Order.UpdateTracking (allocationID, Allocation.Status.ENROUTE_TO_CUSTOMER, replyTo));
        assertTrue (result6.reply ().isSuccess ());
        done = result6.reply ().getValue ();
        timestamp = Instant.now ();
// -- we must extract the timestamp from the actual event, otherwise it won't match the test event
        if (result6.event () instanceof Order.TrackingUpdated trackingEvent) {
            timestamp = trackingEvent.timestamp ();
        }
        assertEquals (new Order.TrackingUpdated (OrderTest.ORDER_ID,
                                                 allocationID,
                                                 Allocation.Status.ENROUTE_TO_CUSTOMER,
                                                 timestamp),
                      result6.event ());
// -- update tracking to PICKED_BY_COURIER - should be rejected - inconsistent status
        CommandResultWithReply<Order.Command,Order.Event,Order.State,StatusReply<Done>>
            result7 =
                eventSourcedTestKit.runCommand (
                    replyTo -> new Order.UpdateTracking (allocationID, Allocation.Status.PICKED_BY_COURIER, replyTo));
        assertTrue (result7.reply ().isError ());
        assertTrue (result7.hasNoEvents ());
    }

}
