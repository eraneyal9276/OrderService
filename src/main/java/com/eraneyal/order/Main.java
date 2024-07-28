package com.eraneyal.order;

import com.eraneyal.order.proto.OrderService;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.Behaviors;
import akka.management.cluster.bootstrap.ClusterBootstrap;
import akka.management.javadsl.AkkaManagement;

import com.typesafe.config.Config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
  * Contains the main method that runs the order service.
  */

public class Main
{

	private static final Logger logger = LoggerFactory.getLogger (Main.class);

/**
  * Starts the actor system and initializes the order service.
  */

	public static void main (String[] args) throws Exception {
		ActorSystem<Void> system = ActorSystem.create (Behaviors.empty (), "OrderService");
		try {
			init (system);
		} catch (Exception e) {
			logger.error ("Terminating due to initialization failure.", e);
			system.terminate ();
		}
	}

/**
  * Initializes the Akka cluster and starts the gRPC server.
  * <p>
  * @param system the actor system
  */

	public static void init (ActorSystem<Void> system) {
		AkkaManagement.get (system).start ();
		ClusterBootstrap.get (system).start ();

		Order.init (system);

		Config config = system.settings ().config ();
		String grpcInterface = config.getString ("order-service.grpc.interface");
		int grpcPort = config.getInt ("order-service.grpc.port");
		OrderService grpcService = new OrderServiceImpl (system);
		OrderServer.start (grpcInterface, grpcPort, system, grpcService);
	}

}
