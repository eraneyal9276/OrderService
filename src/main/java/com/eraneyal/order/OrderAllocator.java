package com.eraneyal.order;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

import java.util.HashMap;
import java.util.Map;

/**
  * Implements an actor that allocates an order, i.e. decides the storage locations from
  * which the ordered items will be collected, as well as the courier that should be booked
  * to deliver them.
  * <p>
  * An order may be split into multiple allocations, which means the ordered items may be
  * collected from multiple storage locations and delivered separately.
  */

public class OrderAllocator extends AbstractBehavior<OrderAllocator.Command>
{

/**
  * Represents the commands supported by this actor.
  */

	public sealed interface Command {}

/**
  * Represents a command to allocate order items for a given customer.
  * <p>
  * @param items the items of the order, indexed by item identifier
  * @param customer the customer of the order
  * @param replyTo a reference to the Order entity that will receive the allocations
  */

	record Allocate (Map<String,OrderItem> items, Customer customer, ActorRef<Order.Command> replyTo)
	implements Command {}

/**
  * Returns a factory for a behavior.
  * <p>
  * @return a factory for a behavior
  */

	public static Behavior<Command> create ()
	{
		return Behaviors.setup (context -> new OrderAllocator (context));
	}

/**
  * Creates a new instance.
  * <p>
  * @param context the actor context
  */

	private OrderAllocator (ActorContext<Command> context)
	{
		super (context);
	}

/**
  * Determines how messages to this actor are processed.
  */

	@Override
	public Receive<Command> createReceive () {
		return newReceiveBuilder ()
				.onMessage (OrderAllocator.Allocate.class, this::onAllocate)
				.build ();
	}

/**
  * Processes a command to allocate ordered items for a given customer.
  * <p>
  * NOTE: The following logic doesn't perform real order allocation (which would require
  *       integration with the inventory of the stores and/or warehouses from which the
  *       items were ordered, to determine from where the order can be fulfilled).
  *       Instead, we only simulate the creation of allocation[s] by using hard-coded
  *       allocations. If the order has a single item, we create a single allocation.
  *       Otherwise, we randomly decide if to create a single allocation for all the items
  *       or to arbitrarily split them into two allocations.
  * <p>
  * @param command the command to allocate ordered items for a given customer
  * @return the new behavior for follow-up messages
  */

	private Behavior<Command> onAllocate (OrderAllocator.Allocate command)
	{
		Map<String,Allocation> allocations = new HashMap<> ();
		Map<String,OrderItem> items = command.items ();
// -- NOTE: though we are not using the customer in this simulation, a real allocation
// --		logic will probably use the customer's address to find the optimal allocation
// --       site and courier service
		Customer customer = command.customer ();

		if (items != null && items.size() > 0 && customer != null) {
			if (items.size () <= 1 || Math.random () < 0.5) {
// -- create a single allocation for all items
				Allocation one = new AllocationBuilder ().setId (1)
													 	 .setItems (items)
													 	 .build ();
				allocations.put (one.getID (), one);
			} else {
// -- split the items into two allocations
				Map.Entry<String,OrderItem> first = items.entrySet ().iterator ().next ();
				items = new HashMap<> (items);
				items.remove (first.getKey ());
// -- a first allocation for the first item
				Allocation one = new AllocationBuilder ().setId (1)
													 	 .setItems (Map.of (first.getKey (),
													 			 			first.getValue ()))
													 	 .build ();
				allocations.put (one.getID (), one);
// -- a second allocation for the remaining items
				Allocation two = new AllocationBuilder ().setId (2)
													 	 .setItems (items)
													 	 .build ();
				allocations.put (two.getID (), two);
			}
// -- send the allocations to the order entity
			command.replyTo ().tell (new Order.ReceiveOrderAllocations (allocations));
		}

// -- the actor should be stopped after the allocations are returned to the order entity
		return Behaviors.stopped ();
	}
}
