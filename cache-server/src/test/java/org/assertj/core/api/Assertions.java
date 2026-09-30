package org.assertj.core.api;

import java.util.Map;
import java.util.Objects;

/**
 * Minimal AssertJ shim for standalone (no-framework) test compilation.
 * Covers every assertion method used in the CacheLab server tests.
 */
public class Assertions {

    // ── factory methods ──────────────────────────────────────────────────────

    public static <T> ObjectAssert<T> assertThat(T actual) {
        return new ObjectAssert<>(actual);
    }

    public static StringAssert assertThat(String actual) {
        return new StringAssert(actual);
    }

    public static IntegerAssert assertThat(int actual) {
        return new IntegerAssert((Integer) actual);
    }

    public static IntegerAssert assertThat(Integer actual) {
        return new IntegerAssert(actual);
    }

    public static LongAssert assertThat(long actual) {
        return new LongAssert(actual);
    }

    public static LongAssert assertThat(Long actual) {
        return new LongAssert(actual);
    }

    public static DoubleAssert assertThat(double actual) {
        return new DoubleAssert(actual);
    }

    public static BooleanAssert assertThat(boolean actual) {
        return new BooleanAssert(actual);
    }

    public static BooleanAssert assertThat(Boolean actual) {
        return new BooleanAssert(actual != null && actual);
    }

    public static IntArrayAssert assertThat(int[] actual) {
        return new IntArrayAssert(actual);
    }

    public static <K, V> MapAssert<K, V> assertThat(Map<K, V> actual) {
        return new MapAssert<>(actual);
    }

    // ── inner assert classes ─────────────────────────────────────────────────

    public static class ObjectAssert<T> {
        protected final T actual;
        protected String desc;

        public ObjectAssert(T actual) { this.actual = actual; }

        public ObjectAssert<T> as(String d) { this.desc = d; return this; }

        public ObjectAssert<T> isEqualTo(Object expected) {
            if (!Objects.equals(actual, expected))
                fail("Expected " + expected + " but was " + actual);
            return this;
        }

        public ObjectAssert<T> isNotEqualTo(Object expected) {
            if (Objects.equals(actual, expected))
                fail("Expected not " + expected + " but was equal");
            return this;
        }

        public ObjectAssert<T> isNull() {
            if (actual != null) fail("Expected null but was " + actual);
            return this;
        }

        public ObjectAssert<T> isNotNull() {
            if (actual == null) fail("Expected non-null but was null");
            return this;
        }

        protected void fail(String msg) {
            throw new AssertionError((desc != null ? "[" + desc + "] " : "") + msg);
        }
    }

    public static class StringAssert extends ObjectAssert<String> {
        public StringAssert(String actual) { super(actual); }

        public StringAssert startsWith(String prefix) {
            if (actual == null || !actual.startsWith(prefix))
                fail("Expected to start with '" + prefix + "' but was '" + actual + "'");
            return this;
        }

        public StringAssert isNotEmpty() {
            if (actual == null || actual.isEmpty())
                fail("Expected non-empty string but was empty/null");
            return this;
        }
    }

    public static class IntegerAssert extends ObjectAssert<Integer> {
        public IntegerAssert(Integer actual) { super(actual); }

        public IntegerAssert isLessThanOrEqualTo(int expected) {
            if (actual == null || actual > expected)
                fail("Expected <= " + expected + " but was " + actual);
            return this;
        }

        public IntegerAssert isGreaterThan(int expected) {
            if (actual == null || actual <= expected)
                fail("Expected > " + expected + " but was " + actual);
            return this;
        }

        public IntegerAssert isGreaterThanOrEqualTo(int expected) {
            if (actual == null || actual < expected)
                fail("Expected >= " + expected + " but was " + actual);
            return this;
        }

        public IntegerAssert isLessThan(int expected) {
            if (actual == null || actual >= expected)
                fail("Expected < " + expected + " but was " + actual);
            return this;
        }
    }

    public static class LongAssert extends ObjectAssert<Long> {
        public LongAssert(long actual) { super(actual); }

        public LongAssert isGreaterThan(long expected) {
            if (actual == null || actual <= expected)
                fail("Expected > " + expected + " but was " + actual);
            return this;
        }

        public LongAssert isGreaterThanOrEqualTo(long expected) {
            if (actual == null || actual < expected)
                fail("Expected >= " + expected + " but was " + actual);
            return this;
        }

        public LongAssert isLessThan(long expected) {
            if (actual == null || actual >= expected)
                fail("Expected < " + expected + " but was " + actual);
            return this;
        }

        public LongAssert isLessThanOrEqualTo(long expected) {
            if (actual == null || actual > expected)
                fail("Expected <= " + expected + " but was " + actual);
            return this;
        }

        public LongAssert isEqualTo(long expected) {
            if (actual == null || actual != expected)
                fail("Expected " + expected + " but was " + actual);
            return this;
        }
    }

    public static class DoubleAssert extends ObjectAssert<Double> {
        public DoubleAssert(double actual) { super(actual); }

        public DoubleAssert isGreaterThanOrEqualTo(double expected) {
            if (actual == null || actual < expected)
                fail("Expected >= " + expected + " but was " + actual);
            return this;
        }

        public DoubleAssert isLessThanOrEqualTo(double expected) {
            if (actual == null || actual > expected)
                fail("Expected <= " + expected + " but was " + actual);
            return this;
        }

        public DoubleAssert isEqualTo(double expected) {
            if (actual == null || actual != expected)
                fail("Expected " + expected + " but was " + actual);
            return this;
        }
    }

    public static class BooleanAssert extends ObjectAssert<Boolean> {
        public BooleanAssert(boolean actual) { super(actual); }

        public BooleanAssert isTrue() {
            if (!Boolean.TRUE.equals(actual)) fail("Expected true but was " + actual);
            return this;
        }

        public BooleanAssert isFalse() {
            if (!Boolean.FALSE.equals(actual)) fail("Expected false but was " + actual);
            return this;
        }
    }

    public static class IntArrayAssert extends ObjectAssert<int[]> {
        public IntArrayAssert(int[] actual) { super(actual); }

        public IntArrayAssert hasSize(int expected) {
            if (actual == null || actual.length != expected)
                fail("Expected size " + expected + " but was " + (actual == null ? "null" : actual.length));
            return this;
        }
    }

    public static class MapAssert<K, V> extends ObjectAssert<Map<K, V>> {
        public MapAssert(Map<K, V> actual) { super(actual); }

        @SuppressWarnings("unchecked")
        public MapAssert<K, V> containsKeys(K... keys) {
            if (actual == null) fail("Map is null");
            for (K key : keys) {
                if (!actual.containsKey(key))
                    fail("Expected map to contain key '" + key + "' but was: " + actual.keySet());
            }
            return this;
        }
    }
}
