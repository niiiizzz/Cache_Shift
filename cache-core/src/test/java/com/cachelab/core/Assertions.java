package com.cachelab.core;

import java.util.Objects;
import java.util.Set;

/**
 * Lightweight Assertions library matching AssertJ style for standalone testing.
 */
public class Assertions {

    public static <T> ObjectAssert<T> assertThat(T actual) {
        return new ObjectAssert<>(actual);
    }

    public static StringAssert assertThat(String actual) {
        return new StringAssert(actual);
    }

    public static IntegerAssert assertThat(Integer actual) {
        return new IntegerAssert(actual);
    }

    public static LongAssert assertThat(Long actual) {
        return new LongAssert(actual);
    }

    public static BooleanAssert assertThat(Boolean actual) {
        return new BooleanAssert(actual);
    }

    public static <E> SetAssert<E> assertThat(Set<E> actual) {
        return new SetAssert<>(actual);
    }

    public static ThrowableAssert assertThatThrownBy(ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Throwable t) {
            return new ThrowableAssert(t);
        }
        throw new AssertionError("Expected exception was not thrown");
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Throwable;
    }

    public static class ObjectAssert<T> {
        protected final T actual;
        protected String description;

        public ObjectAssert(T actual) {
            this.actual = actual;
        }

        public ObjectAssert<T> as(String desc) {
            this.description = desc;
            return this;
        }

        public ObjectAssert<T> isEqualTo(Object expected) {
            if (!Objects.equals(actual, expected)) {
                throw new AssertionError((description != null ? description + ": " : "") + "Expected " + expected + " but got " + actual);
            }
            return this;
        }

        public ObjectAssert<T> isNull() {
            if (actual != null) {
                throw new AssertionError("Expected null but got " + actual);
            }
            return this;
        }

        public ObjectAssert<T> isNotNull() {
            if (actual == null) {
                throw new AssertionError("Expected not null but got null");
            }
            return this;
        }
    }

    public static class StringAssert extends ObjectAssert<String> {
        public StringAssert(String actual) {
            super(actual);
        }
    }

    public static class IntegerAssert extends ObjectAssert<Integer> {
        public IntegerAssert(Integer actual) {
            super(actual);
        }

        public IntegerAssert isLessThanOrEqualTo(int expected) {
            if (actual == null || actual > expected) {
                throw new AssertionError("Expected <= " + expected + " but got " + actual);
            }
            return this;
        }

        public IntegerAssert isGreaterThan(int expected) {
            if (actual == null || actual <= expected) {
                throw new AssertionError("Expected > " + expected + " but got " + actual);
            }
            return this;
        }
    }

    public static class LongAssert extends ObjectAssert<Long> {
        public LongAssert(Long actual) {
            super(actual);
        }

        public LongAssert isGreaterThan(long expected) {
            if (actual == null || actual <= expected) {
                throw new AssertionError("Expected > " + expected + " but got " + actual);
            }
            return this;
        }
    }

    public static class BooleanAssert extends ObjectAssert<Boolean> {
        public BooleanAssert(Boolean actual) {
            super(actual);
        }

        @Override
        public BooleanAssert as(String desc) {
            super.as(desc);
            return this;
        }

        public BooleanAssert isTrue() {
            if (!Boolean.TRUE.equals(actual)) {
                throw new AssertionError((description != null ? description + ": " : "") + "Expected true but got " + actual);
            }
            return this;
        }

        public BooleanAssert isFalse() {
            if (!Boolean.FALSE.equals(actual)) {
                throw new AssertionError((description != null ? description + ": " : "") + "Expected false but got " + actual);
            }
            return this;
        }
    }

    public static class SetAssert<E> extends ObjectAssert<Set<E>> {
        public SetAssert(Set<E> actual) {
            super(actual);
        }
    }

    public static class ThrowableAssert {
        private final Throwable actual;

        public ThrowableAssert(Throwable actual) {
            this.actual = actual;
        }

        public ThrowableAssert isInstanceOf(Class<?> expected) {
            if (actual == null || !expected.isAssignableFrom(actual.getClass())) {
                throw new AssertionError("Expected instance of " + expected + " but got " + (actual == null ? "null" : actual.getClass()));
            }
            return this;
        }
    }
}
