package com.eraneyal.order;

import java.time.Instant;
import java.util.Map;

/**
  * A builder for generating Order.Allocation instances based on hard-coded templates.
  */

public class AllocationBuilder {

/**
  * Holds a hard-coded static registry of allocations.
  */

	private static Allocation[] _allocations;

/**
  * Holds the allocation identifier.
  */

	private int _ident;

/**
  * Holds the order items.
  */

	private Map<String,OrderItem> _items;

/**
  * Creates a new builder instance.
  */

	public AllocationBuilder ()
	{
	}

/**
  * Sets the allocation identifier.
  * <p>
  * @param ident the allocation identifier
  * @return the builder instance
  */

	public AllocationBuilder setId (int ident)
	{
		_ident = ident;

		return this;
	}

/**
  * Sets the items of the allocations.
  * <p>
  * @param items the order items
  * @return the builder instance
  */

	public AllocationBuilder setItems (Map<String,OrderItem> items)
	{
		_items = items;

		return this;
	}

/**
  * Returns a new Order.Allocation instance.
  * <p>
  * @return a new Order.Allocation instance
  */

	public Allocation build ()
	{
		Allocation template = AllocationBuilder._allocations[_ident % AllocationBuilder._allocations.length];

		return new Allocation (
			Integer.toString (_ident),
			template.getName (),
			template.getAddress (),
			_items,
			template.getCourier (),
			null,
			Map.of (Instant.now (), Allocation.Status.ALLOCATED));
	}

	static {

// -- initialize the static registry
		AllocationBuilder._allocations = new Allocation[2];
		AllocationBuilder._allocations[0] = new Allocation (
			"1",
			"TLV Warehouse",
			new Address ("Namir 15", "Tel Aviv", "Israel", 12345),
			Map.of ("dummy-id", new OrderItem ("dummy-id", "name", 1)),
			"FedEx",
			null,
			Map.of (Instant.now (), Allocation.Status.ALLOCATED));
		AllocationBuilder._allocations[1] = new Allocation (
			"2",
			"Outlet Store",
			new Address ("Bialik 89", "Ramat Gan", "Israel", 64722),
			Map.of ("dummy-id", new OrderItem ("dummy-id", "name", 1)),
			"DeliverIt",
			null,
			Map.of (Instant.now (), Allocation.Status.ALLOCATED));

	}

}
