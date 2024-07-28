package com.eraneyal.order;

import akka.serialization.jackson.CborSerializable;

/**
  * Implements an immutable address.
  */

public final class Address implements CborSerializable
{

/**
  * Holds the street name and number.
  */

	private final String _street;

/**
  * Holds the city.
  */

	private final String _city;

/**
  * Hold the country.
  */

	private final String _country;

/**
  * Holds the zip code.
  */

	private final int _zipCode;

/**
  * Creates a new address instances.
  * <p>
  * @param street the street name and number
  * @param city the city
  * @param country the country
  * @param zipCode the zip code
  * <p>
  * NOTE: This constructor supports minimal field validations. A real Address class used
  * 	  in production will also verify maximum field lengths, the correct format of some
  * 	  fields (such as zip code), accepted characters etc...
  * 	  Such validations were not deemed crucial for this assignment.
  */

	public Address (String street, String city, String country, int zipCode)
	{
		if (street == null || street.isBlank ()) {
			throw new IllegalArgumentException ("Address street is missing or empty");
		}
		if (city == null || city.isBlank ()) {
			throw new IllegalArgumentException ("Address city is missing or empty");
		}
		if (country == null || country.isBlank ()) {
			throw new IllegalArgumentException ("Address country is missing or empty");
		}
		if (zipCode < 0) {
			throw new IllegalArgumentException ("ZIP code must be non-negative");
		}

		_street = street;
		_city = city;
		_country = country;
		_zipCode = zipCode;
	}

/**
  * Returns the street name and number.
  * <p>
  * @return the street name and number
  */

	public String getStreet ()
	{
		return _street;
	}

/**
  * Returns the city.
  * <p>
  * @return the city
  */

	public String getCity ()
	{
		return _city;
	}

/**
  * Returns the country.
  * <p>
  * @return the country
  */

	public String getCountry ()
	{
		return _country;
	}

/**
  * Returns the zip code.
  * <p>
  * @return the zip code
  */

	public int getZipCode ()
	{
		return _zipCode;
	}

}
