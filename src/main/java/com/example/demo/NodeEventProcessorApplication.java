package com.example.demo;

/*import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
*/
import com.hazelcast.jet.*;

import com.hazelcast.jet.config.JobConfig;
import com.hazelcast.jet.core.processor.Processors;
import com.hazelcast.jet.datamodel.KeyedWindowResult;
import com.hazelcast.jet.kafka.KafkaSinks;
import com.hazelcast.jet.kafka.KafkaSources;
import com.hazelcast.jet.pipeline.*;
import com.hazelcast.jet.pipeline.test.TestSources;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.*;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.KieModule;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.StatelessKieSession;
import org.kie.internal.io.ResourceFactory;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static com.hazelcast.jet.aggregate.AggregateOperations.counting;
import static com.hazelcast.jet.pipeline.WindowDefinition.sliding;

//@SpringBootApplication
public class NodeEventProcessorApplication {

	public static void main(String[] args) {

		Pipeline pipeline = Pipeline.create();

		// pipeline.readFrom(KafkaSources.<String, Employee>kafka(kafkaProps(),
		// "employee-input"));

		// StreamStage<KeyedWindowResult<String, Employee>> source =
		// (StreamStage<KeyedWindowResult<String, Employee>>)
		// pipeline.readFrom(KafkaSources.<String, Employee>kafka(kafkaProps(),
		// "employee-input"));

//        pipeline.readFrom(TestSources.items("the", "quick", "brown", "fox"))
//        .map(item -> item.toUpperCase())
//        .writeTo(Sinks.logger());

		StreamSource<Map.Entry<String, Employee>> source = KafkaSources
				.<String, Employee>kafka(kafkaProps(), "employee-input");

			pipeline.readFrom(source)
				.withoutTimestamps()
				.mapUsingService(ServiceFactories.sharedService(ctx -> createKieSession()), 
						(kieSession, employee) -> {
					// Initialize the Drools KieSession
					String RULES_CUSTOMER_RULES_DRL = "employee.drl";
					KieServices kieServices = KieServices.Factory.get();
					KieFileSystem kieFileSystem = kieServices.newKieFileSystem();
					// System.out.println("1");
					kieFileSystem.write(ResourceFactory.newClassPathResource(RULES_CUSTOMER_RULES_DRL));
					// System.out.println("2");
					KieBuilder kb = kieServices.newKieBuilder(kieFileSystem);
					// System.out.println("3");
					kb.buildAll();
					// System.out.println("4");
					KieModule kieModule = kb.getKieModule();
					/// System.out.println("5");
//			        KieContainer kieContainer = kieServices.newKieContainer(kieModule.getReleaseId());
					KieContainer kieContainer = kieServices.newKieContainer(kieModule.getReleaseId());
					/// System.out.println("6");
					KieSession kieSessionLocal = kieContainer.newKieSession();
					// KieContainer kieContainer = kieServices.getKieClasspathContainer();
					// return kieContainer.getKieBase().newKieSession();
					kieSession = kieSessionLocal;
					
					kieSession.insert(employee);
					System.out.println("Before rules " + employee);
					kieSession.fireAllRules();
					System.out.println("After rules " + employee);
					kieSession.dispose();
					
					return employee;
				})				
				.writeTo(Sinks.list("employee-input"));
				//.flatMap(List::stream)
//                .mapUsingService(
//                        Processors.nonSharedService(ctx -> createKieSession()),
//                        (kieSession, employee) -> {
//                            kieSession.execute(employee); // Apply Drools rule
//                            
//                            return employee;
//                        }
//                )
                
		
		
		
		
				//.writeTo(Sinks.list("employee"));

		// Create a custom logger for measuring message processing rate
		MessageProcessingRateLogger rateLogger = new MessageProcessingRateLogger();

//         pipeline.readFrom(KafkaSources.<String, Employee>kafka(kafkaProps(), "employee-input"))
//                .map(Map.Entry::getValue)
//         		.map(employee -> {
//                	if (employee.getSalary()> 1500) {
//                		employee.setSalary(10000);
//                	}
//                })
//                //.drainTo(KafkaSinks.kafka(kafkaProps(), "employee-output"));
//                .drainTo(Sinks.logger());

		JobConfig cfg = new JobConfig().setName("kafka-traffic-monitor");
		JetInstance instance = Jet.bootstrappedInstance();
		instance.getMap("employee-input").size();

		instance.newJob(pipeline, cfg).join();
	}
	
	private static KieSession createKieSession() {
        // Initialize the Drools KieSession
        KieServices kieServices = KieServices.Factory.get();
        KieContainer kieContainer = kieServices.getKieClasspathContainer();
        return kieContainer.newKieSession();
    }


	
	private static Properties kafkaProps() {
		Properties props = new Properties();
		props.setProperty("bootstrap.servers", "localhost:9092");
		props.setProperty("key.deserializer", LongDeserializer.class.getCanonicalName());
		props.setProperty("value.deserializer", CustomDeserializer.class.getCanonicalName());
		props.setProperty("auto.offset.reset", "earliest");
		props.setProperty("group.id", "my-consumer-group");
		return props;
	}

	private static Iterable<Employee> traverseEmployees(List<Employee> employeesList) {
		// Extract and return employees from the list
		return employeesList;
	}

	// Custom logger to measure message processing rate
	private static class MessageProcessingRateLogger {
		private long lastCount = 0;
		private long lastTime = System.nanoTime();

		long getProcessingRate(int size) {
			long currentTime = System.nanoTime();
			long elapsedTimeMs = TimeUnit.NANOSECONDS.toMillis(currentTime - lastTime);
			long currentCount = size;

			long messagesProcessed = currentCount - lastCount;

			lastCount = currentCount;
			lastTime = currentTime;

			return messagesProcessed / (elapsedTimeMs / 1000); // Messages per second
		}
	}

}
