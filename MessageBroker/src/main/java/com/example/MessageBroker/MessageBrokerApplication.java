package com.example.MessageBroker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SpringBootApplication
public class MessageBrokerApplication implements CommandLineRunner {
	@Autowired
	private TestClass testClass;

	public static void main(String[] args) {
		SpringApplication.run(MessageBrokerApplication.class, args);
		System.out.println("TEST");
		synchronized (MessageBrokerApplication.class) {
			try {
				MessageBrokerApplication.class.wait();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	@Override
	public void run(String... args) throws Exception {
		testClass.saveUser();
	}

}
