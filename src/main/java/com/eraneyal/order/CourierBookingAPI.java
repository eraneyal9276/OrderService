package com.eraneyal.order;

import java.util.HashMap;
import java.util.Map;

/**
  * Represents a courier API for booking order delivery.
  */

public class CourierBookingAPI {

/**
  * Holds the courier identifier.
  */

	private final String _courierId;

/**
  * Holds the base URI of the booking API.
  */

	private final String _baseURI;

/**
  * Holds the booking handler.
  */

	private final CourierBookingHandler _bookingHandler;

/**
  * Holds the courier identifier representing the default courier.
  */

	public static final String _DEFAULT = "default";

	public CourierBookingAPI (String courierId, String baseUri, CourierBookingHandler  bookingHandler)
	{
		_courierId = courierId;
		_baseURI = baseUri;
		_bookingHandler = bookingHandler;
	}

/**
  * Returns the courier identifier.
  * <p>
  * @return the courier identifier
  */

	public String getCourierId ()
	{
		return _courierId;
	}

/**
  * Returns the base URI of the booking API.
  * <p>
  * @return the base URI of the booking API
  */

	public String getBaseURI ()
	{
		return _baseURI;
	}

/**
  * Returns the booking handler.
  * <p>
  * @return the booking handler
  */

	public CourierBookingHandler getBookingHandler ()
	{
		return _bookingHandler;
	}

/**
  * Returns the booking API matching the passed courier identifier, or the default booking
  * API if the requested courier identifier is not registered.
  * <p>
  * NOTE: The available courier booking APIs have been hard-coded in this class. If these
  * 	  APIs were used in some production software, I would initialize them either from
  * 	  configuration files or from a DB table (depending on the required flexibility).
  * <p>
  * @param courierId the courier identifier
  * @return the booking API matching the passed courier identifier, or the default
  * 		booking API if the requested courier identifier is not registered
  */

	public static CourierBookingAPI getInstanceOrDefault (String courierId)
	{
		CourierBookingAPI api = CourierBookingAPI._availableBookingAPIs.get (courierId);

		if (api == null) {
			api = CourierBookingAPI._availableBookingAPIs.get (CourierBookingAPI._DEFAULT);
		}

		return api;
	}

/**
  * Holds a hard-coded static registry of courier APIs.
  */

	private static Map<String, CourierBookingAPI> _availableBookingAPIs;

	static {

// -- initialize a hard-coded static registry of supported booking APIs.
		CourierBookingAPI._availableBookingAPIs = new HashMap<> ();
	
		CourierBookingAPI._availableBookingAPIs.put (
			"FedEx",
			new CourierBookingAPI (
				"FedEx",
				"http://localhost:8080/courier/fedex-book.jsp",
				new FedExBookingHandlerImpl ()));

		CourierBookingAPI._availableBookingAPIs.put (
			"DeliverIt",
			new CourierBookingAPI (
				"DeliverIt",
				"http://localhost:8080/courier/deliverit-book.jsp",
				new DeliverItBookingHandlerImpl ()));

		CourierBookingAPI._availableBookingAPIs.put (
			CourierBookingAPI._DEFAULT,
			new CourierBookingAPI (
				CourierBookingAPI._DEFAULT,
				"http://www.example.com",
				new FakeBookingHandlerImpl ()));

	}

}
