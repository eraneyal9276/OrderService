package com.eraneyal.order;

import akka.serialization.jackson.CborSerializable;

/**
  * Represents an immutable order item.
  */

public class OrderItem implements CborSerializable
{

/**
  * Holds the item identifier.
  */

    private final String _ident;

/**
  * Holds the item name.
  */

    private final String _name;

/**
  * Holds the item quantity.
  */

    private final int _quantity;

/**
  * Creates a new item instance.
  * <p>
  * @param ident the item identifier
  * @param name the item name
  * @param quantity the item quantity
  * <p>
  * NOTE: This constructor supports minimal field validations. A real OrderItem class used
  * 	  in production will also verify maximum field lengths, accepted characters etc...
  * 	  Such validations were not deemed crucial for this assignment.
  */

    public OrderItem (String itemID, String name, int quantity)
    {
        if (itemID == null || itemID.isBlank ()) {
            throw new IllegalArgumentException ("Item identifier is missing or empty");
        }
        if (name == null || name.isBlank ()) {
            throw new IllegalArgumentException ("Item name is missing or empty");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException ("Item quantity must be positive");
        }

        _ident = itemID;
        _name = name;
        _quantity = quantity;
    }

/**
  * Returns the item identifier.
  * <p>
  * @return the item identifier
  */

    public String getItemID ()
    {
        return _ident;
    }

/**
  * Returns the item name.
  * <p>
  * @return the item name
  */

    public String getName ()
    {
        return _name;
    }

/**
  * Returns the item quantity.
  * <p>
  * @return the item quantity
  */

    public int getQuantity ()
    {
        return _quantity;
    }

}
