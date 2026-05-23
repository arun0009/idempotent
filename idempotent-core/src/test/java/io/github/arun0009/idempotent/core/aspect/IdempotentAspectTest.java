package io.github.arun0009.idempotent.core.aspect;

import io.github.arun0009.idempotent.core.IdempotentProperties;
import io.github.arun0009.idempotent.core.IdempotentTest;
import io.github.arun0009.idempotent.core.annotation.Idempotent;
import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
import io.github.arun0009.idempotent.core.retry.WaitStrategy;
import io.github.arun0009.idempotent.core.service.IdempotentService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotentAspectTest {
    @Mock
    private IdempotentStore idempotentStore;

    @Mock
    private ProceedingJoinPoint proceedingJoinPoint;

    private IdempotentAspect idempotentAspect;

    @BeforeEach
    @SuppressWarnings("resource")
    void setUp() {
        MockitoAnnotations.openMocks(this);
        var idempotentService = new IdempotentService(idempotentStore, new WaitStrategy(5, Duration.ofMillis(100), 2));
        idempotentAspect = new IdempotentAspect(
                idempotentService,
                new IdempotentProperties(
                        "X-Idempotency-Key", new IdempotentProperties.InProgress(5, Duration.ofMillis(100), 2)));
    }

    @Test
    void testAround_newRequest() throws Throwable {
        Method method = this.getClass().getDeclaredMethod("testMethod");
        MethodSignature methodSignature = mock(MethodSignature.class);
        when(proceedingJoinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getParameterNames()).thenReturn(new String[] {"asset"});
        when(methodSignature.getName()).thenReturn("testMethod");
        when(methodSignature.getReturnType()).thenAnswer(invocation -> ResponseEntity.class);
        // Mock the target object
        Object target = this;
        when(proceedingJoinPoint.getTarget()).thenReturn(target);
        when(proceedingJoinPoint.getArgs()).thenReturn(new Object[] {
            new IdempotentTest.Asset("1", new IdempotentTest.AssetType("test-category", "1.0"), "Test API")
        });
        when(methodSignature.getMethod()).thenReturn(method);
        when(proceedingJoinPoint.proceed()).thenReturn(new ResponseEntity<>("response", HttpStatus.OK));
        IdempotentStore.IdempotentKey idempotentKey =
                new IdempotentStore.IdempotentKey("testKey", "__IdempotentAspectTest.testMethod()");
        IdempotentStore.Value value = null;
        when(idempotentStore.getValue(any(IdempotentStore.IdempotentKey.class), any()))
                .thenReturn(value);

        Object response = idempotentAspect.around(proceedingJoinPoint);

        verify(idempotentStore, times(1)).store(eq(idempotentKey), any(IdempotentStore.Value.class));
        verify(idempotentStore, times(1)).update(eq(idempotentKey), any(IdempotentStore.Value.class));
        assertInstanceOf(ResponseEntity.class, response);
        assertEquals("response", ((ResponseEntity<?>) response).getBody());
    }

    @Test
    void testAround_existingRequestInProgress() throws Throwable {
        Method method = this.getClass().getDeclaredMethod("testMethod");
        MethodSignature methodSignature = mock(MethodSignature.class);
        when(proceedingJoinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getParameterNames()).thenReturn(new String[] {"asset"});
        when(proceedingJoinPoint.getArgs()).thenReturn(new Object[] {
            new IdempotentTest.Asset("1", new IdempotentTest.AssetType("test-category", "1.0"), "Test API")
        });
        when(methodSignature.getName()).thenReturn("testMethod");
        when(methodSignature.getReturnType()).thenAnswer(invocation -> ResponseEntity.class);
        // Mock the target object
        Object target = this;
        when(proceedingJoinPoint.getTarget()).thenReturn(target);
        when(methodSignature.getMethod()).thenReturn(method);

        IdempotentStore.IdempotentKey idempotentKey =
                new IdempotentStore.IdempotentKey("testKey", "__IdempotentAspectTest.testMethod()");
        IdempotentStore.Value inProgressValue = new IdempotentStore.Value(
                IdempotentStore.Status.IN_PROGRESS, Instant.now().plusSeconds(10), null);
        when(idempotentStore.getValue(eq(idempotentKey), any()))
                .thenReturn(inProgressValue)
                .thenReturn(new IdempotentStore.Value(
                        IdempotentStore.Status.COMPLETED,
                        Instant.now().plusSeconds(10),
                        new ResponseEntity<>("cached response", HttpStatus.OK)));

        Object response = idempotentAspect.around(proceedingJoinPoint);

        verify(idempotentStore, times(2)).getValue(eq(idempotentKey), any());
        assertInstanceOf(ResponseEntity.class, response);
        assertEquals("cached response", ((ResponseEntity<?>) response).getBody());
    }

    @Test
    void testAround_existingRequestCompleted() throws Throwable {
        Method method = this.getClass().getDeclaredMethod("testMethod");
        MethodSignature methodSignature = mock(MethodSignature.class);
        when(proceedingJoinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getParameterNames()).thenReturn(new String[] {"asset"});
        when(proceedingJoinPoint.getArgs()).thenReturn(new Object[] {
            new IdempotentTest.Asset("1", new IdempotentTest.AssetType("test-category", "1.0"), "Test API")
        });
        when(methodSignature.getName()).thenReturn("testMethod");
        when(methodSignature.getReturnType()).thenAnswer(invocation -> ResponseEntity.class);

        // Mock the target object
        Object target = this;
        when(proceedingJoinPoint.getTarget()).thenReturn(target);
        when(methodSignature.getMethod()).thenReturn(method);

        IdempotentStore.IdempotentKey idempotentKey =
                new IdempotentStore.IdempotentKey("testKey", "__IdempotentAspectTest.testMethod()");
        IdempotentStore.Value completedValue = new IdempotentStore.Value(
                IdempotentStore.Status.COMPLETED,
                Instant.now().plusSeconds(10),
                new ResponseEntity<>("cached response", HttpStatus.OK));
        when(idempotentStore.getValue(any(IdempotentStore.IdempotentKey.class), any()))
                .thenReturn(completedValue);

        Object response = idempotentAspect.around(proceedingJoinPoint);

        verify(idempotentStore, times(1)).getValue(eq(idempotentKey), any());
        assertInstanceOf(ResponseEntity.class, response);
        assertEquals("cached response", ((ResponseEntity<?>) response).getBody());
    }

    @Test
    void testAround_acceptsShortFormDurationAnnotation() throws Throwable {
        Method method = this.getClass().getDeclaredMethod("methodWithShortFormDuration");
        MethodSignature methodSignature = mock(MethodSignature.class);
        when(proceedingJoinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getParameterNames()).thenReturn(new String[] {});
        when(methodSignature.getName()).thenReturn("methodWithShortFormDuration");
        when(methodSignature.getReturnType()).thenAnswer(invocation -> ResponseEntity.class);
        when(proceedingJoinPoint.getTarget()).thenReturn(this);
        when(proceedingJoinPoint.getArgs()).thenReturn(new Object[] {});
        when(methodSignature.getMethod()).thenReturn(method);
        when(proceedingJoinPoint.proceed()).thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));
        when(idempotentStore.getValue(any(IdempotentStore.IdempotentKey.class), any()))
                .thenReturn(null);

        Object response = idempotentAspect.around(proceedingJoinPoint);

        assertInstanceOf(ResponseEntity.class, response);
    }

    @SuppressWarnings("unused")
    @Idempotent(key = "'testKey'", duration = "PT1M")
    private ResponseEntity<String> testMethod() {
        return new ResponseEntity<>("response", HttpStatus.OK);
    }

    @SuppressWarnings("unused")
    @Idempotent(key = "'shortFormKey'", duration = "500ms")
    private ResponseEntity<String> methodWithShortFormDuration() {
        return new ResponseEntity<>("ok", HttpStatus.OK);
    }
}
