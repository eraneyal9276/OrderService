package com.eraneyal.order;

import akka.serialization.jackson.CborSerializable;

/**
  * Represents an immutable customer.
  */

public final class Customer implements CborSerializable
{

/**
  * Holds the customer's first name.
  */

    private final String _firstName;

/**
  * Holds the customer's last name.
  */

    private final String _lastName;

/**
  * Holds the customer's shipping address.
  */

    private final Address _address;

/**
  * Holds the customer's email address.
  */

    private final String _email;

/**
  * Holds the customer's mobile phone number.
  */

    private final String _mobilePhone;

/**
  * Creates a new customer instance.
  * <p>
  * @param firstName the customer's first name
  * @param lastName the customer's last name
  * @param address the customer's shipping address
  * @param email the customer's email address
  * @param mobilePhone the customer's mobile phone number
  * <p>
  * NOTE: This constructor supports minimal field validations. A real Customer class used
  * 	  in production will also verify maximum field lengths, the correct format of some
  * 	  fields (such as email and mobilePhone), accepted characters etc...
  * 	  Such validations were not deemed crucial for this assignment.
  */

    public Customer (
        String firstName,
        String lastName,
        Address address,
        String email,
        String mobilePhone)
    {
        if (firstName == null || firstName.isBlank ()) {
            throw new IllegalArgumentException ("Customer first name is missing or empty");
        }
        if (lastName == null || lastName.isBlank ()) {
            throw new IllegalArgumentException ("Customer last name is missing or empty");
        }
        if (address == null) {
            throw new IllegalArgumentException ("Customer address is missing");
        }
        if (email == null || email.isBlank ()) {
            throw new IllegalArgumentException ("Customer email is missing or empty");
        }
        if (mobilePhone == null || mobilePhone.isBlank ()) {
            throw new IllegalArgumentException ("Customer mobile phone is missing or empty");
        }

        _firstName = firstName;
        _lastName = lastName;
        _address = address;
        _email = email;
        _mobilePhone = mobilePhone;
    }

/**
  * Returns the customer's first name.
  * <p>
  * @return the customer's first name
  */

    public String getFirstName ()
    {
        return _firstName;
    }

/**
  * Returns the customer's last name.
  * <p>
  * @return the customer's last name
  */

    public String getLastName ()
    {
        return _lastName;
    }

/**
  * Returns the customer's shipping address.
  * <p>
  * @return the customer's shipping address
  */

    public Address getAddress ()
    {
        return _address;
    }

/**
  * Returns the customer's email address.
  * <p>
  * @return the customer's email address
  */

    public String getEMail ()
    {
        return _email;
    }

/**
  * Returns the customer's mobile phone number.
  * <p>
  * @return the customer's mobile phone number
  */

    public String getMobilePhone ()
    {
        return _mobilePhone;
    }

}
