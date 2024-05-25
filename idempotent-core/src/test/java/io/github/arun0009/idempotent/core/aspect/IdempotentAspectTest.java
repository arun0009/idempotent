package io.github.arun0009.idempotent.core.aspect;

import io.github.arun0009.idempotent.core.IdempotentTest;
import io.github.arun0009.idempotent.core.annotation.Idempotent;
import io.github.arun0009.idempotent.core.persistence.IdempotentStore;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotentAspectTest {

    @Mock
    private IdempotentStore idempotentStore;

    @Mock
    private ProceedingJoinPoint proceedingJoinPoint;

    private IdempotentAspect idempotentAspect;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        idempotentAspect = new IdempotentAspect(idempotentStore);
        ReflectionTestUtils.setField(idempotentAspect, "idempotentKeyHeader", "X-Idempotency-Key");
        ReflectionTestUtils.setField(idempotentAspect, "inprogressMaxRetries", 5);
        ReflectionTestUtils.setField(idempotentAspect, "inprogressRetryInterval", 100);
        ReflectionTestUtils.setField(idempotentAspect, "inprogressRetryMultiplier", 2);
    }

    @Test
    void testAround_newRequest() throws Throwable {
        Method method = this.getClass().getDeclaredMethod("testMethod");
        MethodSignature methodSignature = mock(MethodSignature.class);
        when(proceedingJoinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getParameterNames()).thenReturn(new String[] {"asset"});
        when(methodSignature.getName()).thenReturn("testMethod");
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
        // Mock the target object
        Object target = this;
        when(proceedingJoinPoint.getTarget()).thenReturn(target);
        when(methodSignature.getMethod()).thenReturn(method);

        IdempotentStore.IdempotentKey idempotentKey =
                new IdempotentStore.IdempotentKey("testKey", "__IdempotentAspectTest.testMethod()");
        IdempotentStore.Value inProgressValue = new IdempotentStore.Value(
                IdempotentStore.Status.INPROGRESS.name(),
                Instant.now().plusSeconds(10).toEpochMilli(),
                null);
        when(idempotentStore.getValue(eq(idempotentKey), any()))
                .thenReturn(inProgressValue)
                .thenReturn(new IdempotentStore.Value(
                        IdempotentStore.Status.COMPLETED.name(),
                        Instant.now().plusSeconds(10).toEpochMilli(),
                        new ResponseEntity<>("cached response", HttpStatus.OK)));

        Object response = idempotentAspect.around(proceedingJoinPoint);

        verify(idempotentStore, atLeast(2)).getValue(eq(idempotentKey), any());
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

        // Mock the target object
        Object target = this;
        when(proceedingJoinPoint.getTarget()).thenReturn(target);
        when(methodSignature.getMethod()).thenReturn(method);

        IdempotentStore.IdempotentKey idempotentKey =
                new IdempotentStore.IdempotentKey("testKey", "__IdempotentAspectTest.testMethod()");
        IdempotentStore.Value completedValue = new IdempotentStore.Value(
                IdempotentStore.Status.COMPLETED.name(),
                Instant.now().plusSeconds(10).toEpochMilli(),
                new ResponseEntity<>("cached response", HttpStatus.OK));
        when(idempotentStore.getValue(any(IdempotentStore.IdempotentKey.class), any()))
                .thenReturn(completedValue);

        Object response = idempotentAspect.around(proceedingJoinPoint);

        verify(idempotentStore, times(1)).getValue(eq(idempotentKey), any());
        assertInstanceOf(ResponseEntity.class, response);
        assertEquals("cached response", ((ResponseEntity<?>) response).getBody());
    }

    @Idempotent(key = "'testKey'", ttlInSeconds = 60, hashKey = false)
    private ResponseEntity<String> testMethod() {
        return new ResponseEntity<>("response", HttpStatus.OK);
    }
}
