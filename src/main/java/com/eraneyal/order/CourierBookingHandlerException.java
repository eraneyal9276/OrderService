package com.eraneyal.order;

/**
  * Represents failures to book order delivery via the courier booking API.
  */

public class CourierBookingHandlerException extends Exception
{

/**
  * Constructs a new exception for a given error message.
  * <p>
  * @param message the error message
  */

    public CourierBookingHandlerException (String message)
    {
        super (message);
    }

/**
  * Constructs a new exception for a given error message and the root cause of this
  * exception.
  * <p>
  * @param message the error message
  * @param cause the root cause of this exception
  */

    public CourierBookingHandlerException (String message, Throwable cause)
    {
        super (message, cause);
    }

}
