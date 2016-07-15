package de.zalando.aruha.nakadi.controller;

import de.zalando.aruha.nakadi.domain.EventType;
import de.zalando.aruha.nakadi.domain.EventTypeOptions;
import de.zalando.aruha.nakadi.problem.ValidationProblem;
import de.zalando.aruha.nakadi.security.Client;
import de.zalando.aruha.nakadi.service.EventTypeService;
import de.zalando.aruha.nakadi.service.Result;
import de.zalando.aruha.nakadi.util.FeatureToggleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.NativeWebRequest;
import org.zalando.problem.spring.web.advice.Responses;

import javax.validation.Valid;
import java.util.List;
import java.util.Objects;

import static de.zalando.aruha.nakadi.util.FeatureToggleService.Feature.DISABLE_EVENT_TYPE_CREATION;
import static de.zalando.aruha.nakadi.util.FeatureToggleService.Feature.DISABLE_EVENT_TYPE_DELETION;

@RestController
@RequestMapping(value = "/event-types")
public class EventTypeController {

    private final EventTypeService eventTypeService;
    private final FeatureToggleService featureToggleService;

    @Autowired
    public EventTypeController(final EventTypeService eventTypeService,
                               final FeatureToggleService featureToggleService)
    {
        this.eventTypeService = eventTypeService;
        this.featureToggleService = featureToggleService;
    }

    @RequestMapping(method = RequestMethod.GET)
    public ResponseEntity<?> list() {
        final List<EventType> eventTypes = eventTypeService.list();

        return ResponseEntity.status(HttpStatus.OK).body(eventTypes);
    }

    @RequestMapping(method = RequestMethod.POST)
    public ResponseEntity<?> create(@Valid @RequestBody final EventType eventType,
                                    final Errors errors,
                                    final NativeWebRequest request)
    {
        if (featureToggleService.isFeatureEnabled(DISABLE_EVENT_TYPE_CREATION)) {
            return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
        }

        ValidationUtils.invokeValidator(new EventTypeOptionsValidator(), eventType.getOptions(), errors);

        if (errors.hasErrors()) {
            return Responses.create(new ValidationProblem(errors), request);
        }

        final Result<Void> result = eventTypeService.create(eventType);
        if (!result.isSuccessful()) {
            return Responses.create(result.getProblem(), request);
        }
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @RequestMapping(value = "/{name:.+}", method = RequestMethod.DELETE)
    public ResponseEntity<?> delete(@PathVariable("name") final String eventTypeName,
                                    final NativeWebRequest request,
                                    final Client client)
    {
        if (featureToggleService.isFeatureEnabled(DISABLE_EVENT_TYPE_DELETION)) {
            return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
        }

        final Result<Void> result = eventTypeService.delete(eventTypeName, client);
        if (!result.isSuccessful()) {
            return Responses.create(result.getProblem(), request);
        }
        return ResponseEntity.status(HttpStatus.OK).build();
    }

    @RequestMapping(value = "/{name:.+}", method = RequestMethod.PUT)
    public ResponseEntity<?> update(
            @PathVariable("name") final String name,
            @RequestBody @Valid final EventType eventType,
            final Errors errors,
            final NativeWebRequest request,
            final Client client)
    {
        if (errors.hasErrors()) {
            return Responses.create(new ValidationProblem(errors), request);
        }
        final Result<Void> update = eventTypeService.update(name, eventType, client);
        if (!update.isSuccessful()) {
            return Responses.create(update.getProblem(), request);
        }
        return ResponseEntity.status(HttpStatus.OK).build();
    }

    @RequestMapping(value = "/{name:.+}", method = RequestMethod.GET)
    public ResponseEntity<?> get(@PathVariable final String name, final NativeWebRequest request) {
        final Result<EventType> result = eventTypeService.get(name);
        if (!result.isSuccessful()) {
            return Responses.create(result.getProblem(), request);
        }
        return ResponseEntity.status(HttpStatus.OK).body(result.getValue());
    }

    class EventTypeOptionsValidator implements Validator {

        @Value("${nakadi.topic.default.retentionMs}")
        private long maxTopicRetentionMs;

        @Value("${nakadi.topic.min.retentionMs}")
        private long minTopicRetentionMs;

        @Override
        public boolean supports(Class<?> clazz) {
            return EventTypeOptions.class.equals(clazz);
        }

        @Override
        public void validate(Object target, Errors errors) {
            EventTypeOptions options = (EventTypeOptions) target;
            if (Objects.nonNull(options) && Objects.nonNull(options.getRetentionTime())) {
                Long retentionTime = options.getRetentionTime();
                if (retentionTime > maxTopicRetentionMs) {
                    errors.rejectValue("retentionTime", "retentionTime can not be bigger than " + maxTopicRetentionMs);
                } else if (retentionTime < minTopicRetentionMs) {
                    errors.rejectValue("retentionTime", "retentionTime can not be less than " + minTopicRetentionMs);
                }
            }
        }
    }
}
