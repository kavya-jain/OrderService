package com.example.orderservice;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.access.StateMachineAccess;
import org.springframework.statemachine.access.StateMachineFunction;
import org.springframework.statemachine.action.Action;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.StateMachineConfigurerAdapter;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.statemachine.state.State;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.statemachine.support.StateMachineInterceptorAdapter;
import org.springframework.statemachine.transition.Transition;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
class Orders {
    @Id
    @GeneratedValue
    private Long id;
    private Date dateTime;
    private String state;

    Orders(OrderState os, Date d) {
        this.state = os.name();
        this.dateTime = d;
    }

    public OrderState getOrderState(){
        return OrderState.valueOf(this.state);
    }

    public void setOrderState(OrderState os){
        this.state = os.name();
    }

}

enum OrderState {
    SUBMITTED,
  PAID,
  FULFILLED,
  CANCELLED

}
enum OrderEvent {
  PAY,
  FULFIL,
  CANCEL

}
@SpringBootApplication
public class OrderServiceApplication {

    public static void main(String[] args) {
    SpringApplication.run(OrderServiceApplication.class, args);
  }

}
@Component
@Slf4j
class Runner implements ApplicationRunner {


//	private final StateMachineFactory<OrderState, OrderEvent> factory;

    @Autowired
	private OrderService orderService;
	/*@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
	Runner(StateMachineFactory<OrderState, OrderEvent> factory){
		this.factory = factory;
	}*/
    //    Runner(OrderService orderService){
    //        this.orderService = orderService;

//    }
    @Override public void run(ApplicationArguments args) throws Exception {

	    Orders order = orderService.create(new Date());
        log.info("create order state {}", order.getOrderState().name());
        StateMachine<OrderState, OrderEvent> paymentSM = orderService.pay(order.getId(), UUID.randomUUID().toString());
        log.info("payment state {}", paymentSM.getState().getId().name());
        StateMachine<OrderState, OrderEvent> fulfillSM = orderService.fulfill(order.getId());
        log.info("fulfill state {}", fulfillSM.getState().getId().name());


	    /*
	    Long orderId = 1234L;
		StateMachine<OrderState, OrderEvent> machine = this.factory.getStateMachine(orderId.toString());
        machine.getExtendedState().getVariables().putIfAbsent("orderId", orderId);
		machine.start();
		log.info("current state: {}", machine.getState().getId().name());
		machine.sendEvent(OrderEvent.PAY);
        log.info("current state: {}", machine.getState().getId().name());
        //Messaging support
        Message<OrderEvent> message = MessageBuilder
                .withPayload(OrderEvent.FULFIL)
                .setHeader("a", "b")
                .build();
        machine.sendEvent(message);
        log.info("current state: {}", machine.getState().getId().name());
        */
	}

}
@Slf4j
@Configuration
@EnableStateMachineFactory // ability to keep one global state m/c or to vend new instances of the
                           // state m/c; Factory Mechanism
class SimpleStateMachineConfiguration
    extends StateMachineConfigurerAdapter<OrderState, OrderEvent> {

    // override the engine

  @Override
  public void configure(StateMachineConfigurationConfigurer<OrderState, OrderEvent> config)
      throws Exception {
    config.withConfiguration().autoStartup(false).listener(getStateMachineListener());
  }
    // tell SM about the states

  @Override public void configure(StateMachineStateConfigurer<OrderState, OrderEvent> states) throws Exception {
//  	states.withStates().initial(OrderState.SUBMITTED).state(OrderState.PAID).end(OrderState.FULFILLED).end(OrderState.CANCELLED);
      // provide handlers for events
      states.withStates()
              .initial(OrderState.SUBMITTED)
              .stateEntry(OrderState.SUBMITTED, getSubmittedHandler())
              .state(OrderState.PAID)
              .end(OrderState.FULFILLED)
              .end(OrderState.CANCELLED);
  }

    private Action<OrderState, OrderEvent> getSubmittedHandler() {
        return new Action<OrderState, OrderEvent>() {
            @Override public void execute(StateContext<OrderState, OrderEvent> context) {
                Long orderId = Long.class.cast(context.getExtendedState().getVariables().getOrDefault("orderId", -1L));
                log.info("entering submitted state, orderId : {}", orderId);
            }
        };
    }
    /* configure the transitions
    Local : A->B->C->..._>Z without any intervention
     */

	@Override
	public void configure(StateMachineTransitionConfigurer<OrderState, OrderEvent> transitions)
			throws Exception {
		transitions
				.withExternal().source(OrderState.SUBMITTED).target(OrderState.PAID).event(OrderEvent.PAY)
				.and()
				.withExternal().source(OrderState.PAID).target(OrderState.FULFILLED).event(OrderEvent.FULFIL)
				.and()
				.withExternal().source(OrderState.SUBMITTED).target(OrderState.CANCELLED).event(OrderEvent.CANCEL)
				.and()
				.withExternal().source(OrderState.PAID).target(OrderState.CANCELLED).event(OrderEvent.CANCEL);

	}
    private StateMachineListenerAdapter<OrderState, OrderEvent> getStateMachineListener() {
    return new StateMachineListenerAdapter<OrderState, OrderEvent>() {
      @Override
      public void stateChanged(
          State<OrderState, OrderEvent> from, State<OrderState, OrderEvent> to) {
        log.info("state changed from :{}, to:{}", from, to);
      }
    };
  }
}

@Service
class OrderService {

    private OrderRepository orderRepository;
    private final StateMachineFactory<OrderState, OrderEvent> stateMachineFactory;

    public OrderService(OrderRepository orderRepository, StateMachineFactory<OrderState, OrderEvent> stateMachineFactory) {
        this.orderRepository = orderRepository;
        this.stateMachineFactory = stateMachineFactory;
    }

    public Orders create(Date date){
        return orderRepository.save(new Orders(OrderState.SUBMITTED, date));
    }

    StateMachine<OrderState, OrderEvent> fulfill(Long orderId) {
        StateMachine<OrderState, OrderEvent> sm = this.build(orderId);
        Message message = MessageBuilder.withPayload(OrderEvent.FULFIL)
                .setHeader("orderId", orderId).build();
        sm.sendEvent(message);
        return sm;
    }

    StateMachine<OrderState, OrderEvent> pay(Long orderId, String paymentConfirmation){
        StateMachine<OrderState, OrderEvent> sm = this.build(orderId);
        Message message = MessageBuilder.withPayload(OrderEvent.PAY)
                .setHeader("orderId", orderId).setHeader("paymentConfirmation", paymentConfirmation).build();
        sm.sendEvent(message);
        return sm;
    }

    private StateMachine<OrderState, OrderEvent> build(Long orderId) {
        Orders order = orderRepository.findById(orderId).orElse(null);
        String orderIdKey = Long.toString(order.getId());
        StateMachine<OrderState, OrderEvent> sm = this.stateMachineFactory.getStateMachine(orderIdKey);
        sm.stop();
        /*
        SM needs to be reset to the order's current state otherwise it will start from initial state: SUBMITTED
        Accessor : helps to visit nodes in the directed graph i.e. state machine
         */
        sm.getStateMachineAccessor().doWithAllRegions(new StateMachineFunction<StateMachineAccess<OrderState, OrderEvent>>() {
            @Override public void apply(StateMachineAccess<OrderState, OrderEvent> sma) {
                // when the state changes, SM needs to persist it
                sma.addStateMachineInterceptor(new StateMachineInterceptorAdapter<OrderState, OrderEvent>(){
                    @Override public void preStateChange(State<OrderState, OrderEvent> state,
                            Message<OrderEvent> message, Transition<OrderState, OrderEvent> transition,
                            StateMachine<OrderState, OrderEvent> stateMachine) {
                        Optional.ofNullable(message).ifPresent(msg -> {
                            Optional.ofNullable(Long.class.cast(msg.getHeaders().getOrDefault("orderId", -1L)))
                                    .ifPresent(orderId -> {
                                        Orders order = orderRepository.findById(orderId).get();
                                        order.setOrderState(state.getId());
                                        orderRepository.save(order);
                                    });
                                });
                    }
                });
                // forcing SM to start from order's current state
                sma.resetStateMachine(new DefaultStateMachineContext<>(order.getOrderState(), null, null, null));
            }
        });
        sm.start();
        return sm;
    }

}

@Repository
interface OrderRepository extends JpaRepository<Orders, Long> {

}