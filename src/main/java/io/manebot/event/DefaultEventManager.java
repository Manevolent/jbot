package io.manebot.event;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class DefaultEventManager implements EventManager, EventDispatcher {
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final LinkedHashMap<Class<? extends Event>, List<EventAction>> eventMap = new LinkedHashMap<>();

    public DefaultEventManager() {

    }

    @Override
    public void registerListener(EventListener eventListener) {
        synchronized (eventMap) {
            for (EventAction action : getActions(eventListener)) {
                List<EventAction> actions = eventMap.computeIfAbsent(
                        action.getEventClass(),
                        k -> new CopyOnWriteArrayList<>()
                );

                actions.add(action);
            }
        }
    }

    @Override
    public void unregisterListener(EventListener eventListener) {
        synchronized (eventMap) {
            for (EventAction action : getActions(eventListener)) {
                List<EventAction> actions = eventMap.get(action.getEventClass());
                if (actions == null) return;

                actions.removeIf(x -> x.getEventExecutor().getListener() == eventListener);
            }
        }
    }

    @Override
    public <T extends Event> Future<T> executeAsync(T event) {
        return executorService.submit(() -> execute(event));
    }

    @Override
    public <T extends Event> T execute(T event) throws EventExecutionException {
        List<EventAction> actions = eventMap.get(event.getClass());
        if (actions == null) return event;

        for (EventAction action : actions) action.getEventExecutor().fire(event);

        return event;
    }

    @SuppressWarnings("unchecked")
    private List<EventAction> getActions(EventListener listener) {
        List<EventAction> actions = new LinkedList<>();

        for (Method method : listener.getClass().getMethods()) {
            EventHandler annotation = method.getAnnotation(EventHandler.class);
            if (annotation == null) continue;

            if (method.getReturnType() != void.class)
                throw new IllegalArgumentException(
                        "Method has invalid return type: " +
                        method.toGenericString() + ": " +
                        method.getReturnType().getName() + " != " + void.class.getName()
                );

            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length != 1)
                throw new IllegalArgumentException(method.toGenericString()
                        + ": parameters (" + parameters.length + ") != 1");

            Class<?> parameter0 = parameters[0];
            if (!Event.class.isAssignableFrom(parameter0))
                throw new IllegalArgumentException(
                        method.toGenericString() +
                                ": parameter class " + parameter0.getName()
                        + " is not extensible by " + Event.class.getName()
                );

            actions.add(new EventAction(
                    new DefaultEventExecutor(
                        listener,
                        annotation.priority(),
                        method
                    ),
                    (Class<? extends Event>) parameter0
            ));
        }

        return actions;
    }

    private class EventAction {
        private final EventExecutor eventExecutor;
        private final Class<? extends Event> eventClass;

        private EventAction(EventExecutor eventExecutor, Class<? extends Event> eventClass) {
            this.eventExecutor = eventExecutor;
            this.eventClass = eventClass;
        }

        private EventExecutor getEventExecutor() {
            return eventExecutor;
        }

        private Class<? extends Event> getEventClass() {
            return eventClass;
        }
    }
}
