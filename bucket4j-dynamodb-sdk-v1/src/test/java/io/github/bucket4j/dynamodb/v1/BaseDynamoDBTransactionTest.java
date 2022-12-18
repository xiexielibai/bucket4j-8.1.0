package io.github.bucket4j.dynamodb.v1;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.document.ItemUtils;
import com.amazonaws.services.dynamodbv2.local.embedded.DynamoDBEmbedded;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public abstract class BaseDynamoDBTransactionTest<K> {
    protected static final AmazonDynamoDB db = DynamoDBEmbedded.create().amazonDynamoDB();
    protected static final String table = "buckets";

    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    @Before
    public void createStateTable() {
        Utils.createStateTable(db, table, keyType());
    }

    @After
    public void tearDown() {
        db.deleteTable(table);
    }

    @Test
    public void ctorThrowsIfDynamoDBIsNull() {
        thrown.expect(NullPointerException.class);
        thrown.expectMessage("DynamoDB is null");

        new NoopDynamoDBTransaction(null, "buckets");
    }

    @Test
    public void ctorThrowsIfTableNameIsNull() {
        thrown.expect(NullPointerException.class);
        thrown.expectMessage("table name is null");

        new NoopDynamoDBTransaction(db, null);
    }

    @Test
    public void ctorThrowsIfKeyIsNull() {
        thrown.expect(NullPointerException.class);
        thrown.expectMessage("key is null");

        transaction(null);
    }

    @Test
    public void getReturnsEmptyIfNoRecordExists() {
        BaseDynamoDBTransaction transaction = transaction(key());

        Optional<byte[]> state = transaction.getStateData();
        assertFalse(state.isPresent());
    }

    @Test
    public void getReturnsEmptyIfNoStateAttrExists() {
        // given
        K key = key();

        saveState(key, null);

        BaseDynamoDBTransaction transaction = transaction(key);

        // when
        Optional<byte[]> state = transaction.getStateData();

        // then
        assertFalse(state.isPresent());
    }

    @Test
    public void getThrowsIfStateContainsValueDifferentFromBinary() {
        // given
        K key = key();
        String state = "not a bucket state";

        saveState(key, state);

        BaseDynamoDBTransaction transaction = transaction(key);

        // then
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage(
                "state (attribute: state) value is corrupted for key " +
                ItemUtils.toAttributeValue(key) +
                ". It is present but value type is different from Binary (B) type. " +
                "Current state value is " + ItemUtils.toAttributeValue(state)
        );

        transaction.getStateData();
    }

    @Test
    public void getReturnsStateWhenPresent() {
        // given
        K key = key();
        byte[] state = new byte[]{0, 1, 2, 3};

        saveState(key, state);

        BaseDynamoDBTransaction transaction = transaction(key);

        // when
        Optional<byte[]> actual = transaction.getStateData();

        // then
        assertTrue(actual.isPresent());
        assertArrayEquals(state, actual.get());
    }

    @Test
    public void compareAndSwapReturnsTrueIfNoOriginalDataIsStored() {
        // given
        K key = key();
        byte[] original = null;
        byte[] updated = new byte[]{0, 1, 2, 3};
        BaseDynamoDBTransaction transaction = transaction(key);

        // when
        boolean result = transaction.compareAndSwap(original, updated, null);
        byte[] actual = getState(key);
        
        // then
        assertTrue(result);
        assertArrayEquals(updated, actual);
    }

    @Test
    public void compareAndSwapReturnsTrueIfOriginalDataIsEqualToNew() {
        // given
        K key = key();
        byte[] original = new byte[]{0, 1, 2, 3};
        byte[] updated = new byte[]{0, 1, 2, 4};

        saveState(key, original);

        BaseDynamoDBTransaction transaction = transaction(key);

        // when
        boolean result = transaction.compareAndSwap(original, updated, null);
        byte[] actual = getState(key);

        // then
        assertTrue(result);
        assertArrayEquals(updated, actual);
    }

    @Test
    public void compareAndSwapReturnsFalseIfOriginalDataIsNotEqualToNew() {
        // given
        K key = key();
        byte[] original = new byte[]{0, 1, 2, 3};
        byte[] stored = new byte[]{0, 1, 2, 4};
        byte[] updated = new byte[]{0, 1, 2, 5};

        saveState(key, stored);

        BaseDynamoDBTransaction transaction = transaction(key);

        // when
        boolean result = transaction.compareAndSwap(original, updated, null);
        byte[] actual = getState(key);

        // then
        assertFalse(result);
        assertArrayEquals(stored, actual);
    }

    @Test
    public void compareAndSwapReturnsFalseIfStoredDataTypeIsDifferentFromBinary() {
        // given
        K key = key();
        byte[] original = new byte[]{0, 1, 2, 3};
        String stored = "not a bucket state";
        byte[] updated = new byte[]{0, 1, 2, 4};

        saveState(key, stored);

        BaseDynamoDBTransaction transaction = transaction(key);

        // when
        boolean result = transaction.compareAndSwap(original, updated, null);
        String actual = getState(key);

        // then
        assertFalse(result);
        assertEquals(stored, actual);
    }

    /**
     * @return {@link ScalarAttributeType} of key.
     */
    protected abstract ScalarAttributeType keyType();

    /**
     * @return key to use for test. Each invocation <i>may</i> return new key. It is
     * up to invoker to store returned value.
     */
    protected abstract K key();

    protected abstract BaseDynamoDBTransaction transaction(K key);

    private static <K> void saveState(K key, Object state) {
        Map<String, Object> item = new HashMap<>();

        item.put(Constants.Attrs.DEFAULT_KEY_NAME, key);
        if (state != null) {
            item.put(Constants.Attrs.DEFAULT_STATE_NAME, state);
        }

        db.putItem(table, ItemUtils.fromSimpleMap(item));
    }

    private static <K, V> V getState(K key) {
        Map<String, AttributeValue> attrs = ItemUtils.fromSimpleMap(Collections.singletonMap(
                Constants.Attrs.DEFAULT_KEY_NAME, key
        ));

        Map<String, AttributeValue> item = db.getItem(table, attrs, true).getItem();
        if (item == null) {
            return null;
        }

        return ItemUtils.toSimpleValue(item.getOrDefault(Constants.Attrs.DEFAULT_STATE_NAME, null));
    }

    private static class NoopDynamoDBTransaction extends BaseDynamoDBTransaction {
        protected NoopDynamoDBTransaction(AmazonDynamoDB db, String table) {
            super(db, table);
        }

        @Override
        protected AttributeValue getKeyAttributeValue() {
            throw new UnsupportedOperationException("not implemented (as expected)");
        }
    }
}
