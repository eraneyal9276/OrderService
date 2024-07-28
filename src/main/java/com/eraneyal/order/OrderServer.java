package com.eraneyal.order;

import com.eraneyal.order.proto.OrderService;
import com.eraneyal.order.proto.OrderServiceHandlerFactory;

import akka.actor.typed.ActorSystem;
import akka.grpc.javadsl.ServerReflection;
import akka.grpc.javadsl.ServiceHandler;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.japi.function.Function;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.CompletionStage;

/**
  * An Akka HTTP server that runs gRPC services implementation.
  */

public final class OrderServer
{

/**
  * Create a new instance.
  */

	private OrderServer () {}

/**
  * Starts the HTTP server.
  * <p>
  * @param host the server host
  * @param port the server port
  * @param system the actor system
  * @param grpcService the gRPC services implementation
  */

	static void start (String host, int port, ActorSystem<?> system, OrderService grpcService)
	{
		@SuppressWarnings ("unchecked")
		Function<HttpRequest, CompletionStage<HttpResponse>> service =
			ServiceHandler.concatOrNotFound (
				OrderServiceHandlerFactory.create (grpcService, system),
				// ServerReflection enabled to support grpcurl without import-path and proto parameters
				ServerReflection.create (
					Collections.singletonList (OrderService.description), system));

		CompletionStage<ServerBinding> bound =
			Http.get (system).newServerAt (host, port).bind (service);

		bound.whenComplete (
			(binding, ex) -> {
				if (binding != null) {
					binding.addToCoordinatedShutdown (Duration.ofSeconds (3), system);
					InetSocketAddress address = binding.localAddress ();
					system
						.log ()
						.info (
							"Order online at gRPC server {}:{}",
							address.getHostString (),
							address.getPort ());
				} else {
					system.log ().error ("Failed to bind gRPC endpoint, terminating system", ex);
					system.terminate ();
				}
			});
	}

}