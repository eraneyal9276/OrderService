package com.eraneyal.order;

import akka.serialization.jackson.CborSerializable;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
  * Represents an immutable allocation of an entire order or a subset of an offer's items.
  */

public final class Allocation implements CborSerializable
{

/**
  * Represents the status of an order allocation.
  */

    public static enum Status {
        NA,						// not applicable
        CREATED,				// a newly created order without allocations
        ALLOCATED, 				// allocation created
        PACKED, 				// allocation items packed and booked for delivery
        PICKED_BY_COURIER, 		// courier picked the allocation items for delivery
        ENROUTE_TO_CUSTOMER, 	// courier is on the way to the customer
        DELIVERED 				// allocation items were delivered to customer
    }

/**
  * Holds the allocation identifier.
  */

    private final String _ident;

/**
  * Holds the name of the location where the items will be collected and packed.
  */

    private final String _name;

/**
  * Holds the address of the location where the items will be collected and packed.
  */

    private final Address _address;

/**
  * Holds the order items to be handled by this allocation.
  */

    private final Map<String,OrderItem> _items;

/**
  * Holds the identifier of the courier that will be booked to deliver the items of this
  * allocation.
  */

    private final String _courier;

/**
  * Holds the tracking identifier returned by the courier's booking API.
  */

    private final String _tracking;

/**
  * Holds the processing statuses of this allocation, indexed by timestamp.
  */

    private final SortedMap<Instant,Status> _statuses;

/**
  * Creates a new allocation instance.
  * <p>
  * @param ident the allocation identifier
  * @param name the name of the location where the items will be collected and packed
  * @param address the address of the location where the items will be collected and packed
  * @param items the order items to be handled by this allocation
  * @param courierId the courier identifier
  * @param trackingId the tracking identifier
  * @param statuses the processing statuses of this allocation
  */

    public Allocation (
        String ident,
        String name,
        Address address,
        Map<String,OrderItem> items,
        String courierId,
        String trackingId,
        Map<Instant,Status> statuses)
    {
        _ident = ident;
        _name = name;
        _address = address;
        _items = new HashMap<> (items);
        _courier = courierId;
        _tracking = trackingId;
        _statuses = new TreeMap<> (statuses);
    }

/**
  * Returns the allocation identifier.
  * <p>
  * @return the allocation identifier
  */

    public String getID ()
    {
        return _ident;
    }

/**
  * Returns the name of the location where the items will be collected and packed.
  * <p>
  * @return the name of the location where the items will be collected and packed
  */

    public String getName ()
    {
        return _name;
    }

/**
  * Returns the address of the location where the items will be collected and packed.
  * <p>
  * @return the address of the location where the items will be collected and packed
  */

    public Address getAddress ()
    {
        return _address;
    }

/**
  * Returns the order items to be handled by this allocation.
  * <p>
  * @return the order items to be handled by this allocation
  */

    public Map<String,OrderItem> getItems ()
    {
        return new HashMap<> (_items);
    }

/**
  * Returns the identifier of the courier that will be booked to deliver the items of this
  * allocation.
  * <p>
  * @return the courier identifier
  */

    public String getCourier ()
    {
        return _courier;
    }

/**
  * Returns the tracking identifier returned by the courier's booking API.
  * <p>
  * @return the tracking identifier returned by the courier's booking API
  */

    public String getTrackingID ()
    {
        return _tracking;
    }

/**
  * Returns the processing statuses of this allocation, indexed and ordered by timestamp.
  * <p>
  * @return the processing statuses of this allocation, indexed by and ordered by
  * 		timestamp
  */

    public SortedMap<Instant,Status> getStatuses ()
    {
        return new TreeMap<> (_statuses);
    }

/**
  * Returns the latest (most current) processing status of this allocation.
  * <p>
  * @return the latest (most current) processing status of this allocation
  */

    public Status getLatestAllocationStatus ()
    {
        Status latest = Status.NA;

        if (!_statuses.isEmpty ()) {
            latest = _statuses.lastEntry ()
                              .getValue ();
        }

        return latest;
    }

}
