package nars.util.data;

//https://raw.githubusercontent.com/EsotericSoftware/kryo/master/src/com/esotericsoftware/kryo/util/ObjectMap.java
/* Copyright (c) 2008, Nathan Sweet
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution.
 * - Neither the name of Esoteric Software nor the names of its contributors may be used to endorse or promote products derived
 * from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
 * BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
 * SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. */


import com.google.common.collect.Sets;
import jdk.nashorn.internal.objects.Global;
import org.jgrapht.util.ArrayUnenforcedSet;

import java.util.*;

/** An unordered map. This implementation is a cuckoo hash map using 3 hashes, random walking, and a small stash for problematic
 * keys. Null keys are not allowed. Null values are allowed. No allocation is done except when growing the table size. <br>
 * <br>
 * This map performs very fast get, containsKey, and remove (typically O(1), worst case O(log(n))). Put may be a bit slower,
 * depending on hash collisions. Load factors greater than 0.91 greatly increase the chances the map will have to rehash to the
 * next higher POT size.
 * @author Nathan Sweet */
public class CuckooMap<K, V> implements Map<K,V> {
    private static final int PRIME1 = 0xbe1f14b1;
    private static final int PRIME2 = 0xb4b82e39;
    private static final int PRIME3 = 0xced1c241;

    static Random random = XORShiftRandom.global;

    public int size;

    public K[] keyTable;
    public V[] valueTable;
    int capacity, stashSize;

    private float loadFactor;
    private int hashShift, mask, threshold;
    private int stashCapacity;
    private int pushIterations;

    /** Creates a new map with an initial capacity of 32 and a load factor of 0.8. This map will hold 25 items before growing the
     * backing table. */
    public CuckooMap() {
        this(32, 0.8f);
    }

    /** Creates a new map with a load factor of 0.8. This map will hold initialCapacity * 0.8 items before growing the backing
     * table. */
    public CuckooMap(int initialCapacity) {
        this(initialCapacity, 0.8f);
    }

    /** Creates a new map with the specified initial capacity and load factor. This map will hold initialCapacity * loadFactor items
     * before growing the backing table. */
    public CuckooMap(int initialCapacity, float loadFactor) {
        if (initialCapacity < 0) throw new IllegalArgumentException("initialCapacity must be >= 0: " + initialCapacity);
        if (initialCapacity > 1 << 30) throw new IllegalArgumentException("initialCapacity is too large: " + initialCapacity);
        capacity = nextPowerOfTwo(initialCapacity);

        if (loadFactor <= 0) throw new IllegalArgumentException("loadFactor must be > 0: " + loadFactor);
        this.loadFactor = loadFactor;

        threshold = (int)(capacity * loadFactor);
        mask = capacity - 1;
        hashShift = 31 - Integer.numberOfTrailingZeros(capacity);
        stashCapacity = Math.max(3, (int)Math.ceil(Math.log(capacity)) * 2);
        pushIterations = Math.max(Math.min(capacity, 8), (int)Math.sqrt(capacity) / 8);

        keyTable = (K[])new Object[capacity + stashCapacity];
        valueTable = (V[])new Object[keyTable.length];
    }

    /** Creates a new map identical to the specified map. */
    public CuckooMap(CuckooMap<? extends K, ? extends V> map) {
        this(map.capacity, map.loadFactor);
        stashSize = map.stashSize;
        System.arraycopy(map.keyTable, 0, keyTable, 0, map.keyTable.length);
        System.arraycopy(map.valueTable, 0, valueTable, 0, map.valueTable.length);
        size = map.size;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }


    @Override
    public boolean containsValue(Object value) {
        return false;
    }


    /** Returns the old value associated with the specified key, or null. */
    @Override
    public V put (K key, V value) {
        if (key == null) throw new IllegalArgumentException("key cannot be null.");
        return put_internal(key, value);
    }


    @Override
    public void putAll(Map<? extends K, ? extends V> m) {

    }

    private V put_internal (K key, V value) {
        final K[] keyTable = this.keyTable;
        final V[] vt = valueTable;

        // Check for existing keys.
        int hashCode = key.hashCode();
        int index1 = hashCode & mask;
        K key1 = keyTable[index1];
        if (key.equals(key1)) {
            V oldValue = vt[index1];
            vt[index1] = value;
            return oldValue;
        }

        int index2 = hash2(hashCode);
        K key2 = keyTable[index2];
        if (key.equals(key2)) {
            V oldValue = vt[index2];
            vt[index2] = value;
            return oldValue;
        }

        int index3 = hash3(hashCode);
        K key3 = keyTable[index3];
        if (key.equals(key3)) {
            V oldValue = vt[index3];
            vt[index3] = value;
            return oldValue;
        }

        // Update key in the stash.
        final int st = stashSize;
        for (int i = capacity, n = i + st; i < n; i++) {
            if (key.equals(keyTable[i])) {
                V oldValue = vt[i];
                vt[i] = value;
                return oldValue;
            }
        }

        // Check for empty buckets.
        if (key1 == null) {
            keyTable[index1] = key;
            vt[index1] = value;
            if (size++ >= threshold) resize(capacity << 1);
            return null;
        }

        if (key2 == null) {
            keyTable[index2] = key;
            vt[index2] = value;
            if (size++ >= threshold) resize(capacity << 1);
            return null;
        }

        if (key3 == null) {
            keyTable[index3] = key;
            vt[index3] = value;
            if (size++ >= threshold) resize(capacity << 1);
            return null;
        }

        push(key, value, index1, key1, index2, key2, index3, key3);
        return null;
    }

    public void putAll (CuckooMap<K, V> map) {
        ensureCapacity(map.size);
        for (Entry<K, V> entry : map.entries())
            put(entry.key, entry.value);
    }

    /** Skips checks for existing keys. */
    private void putResize (K key, V value) {
        // Check for empty buckets.
        int hashCode = key.hashCode();
        int index1 = hashCode & mask;
        K key1 = keyTable[index1];
        if (key1 == null) {
            keyTable[index1] = key;
            valueTable[index1] = value;
            if (size++ >= threshold) resize(capacity << 1);
            return;
        }

        int index2 = hash2(hashCode);
        K key2 = keyTable[index2];
        if (key2 == null) {
            keyTable[index2] = key;
            valueTable[index2] = value;
            if (size++ >= threshold) resize(capacity << 1);
            return;
        }

        int index3 = hash3(hashCode);
        K key3 = keyTable[index3];
        if (key3 == null) {
            keyTable[index3] = key;
            valueTable[index3] = value;
            if (size++ >= threshold) resize(capacity << 1);
            return;
        }

        push(key, value, index1, key1, index2, key2, index3, key3);
    }

    private void push (K insertKey, V insertValue, int index1, K key1, int index2, K key2, int index3, K key3) {
        final K[] keyTable = this.keyTable;
        final V[] valueTable = this.valueTable;
        final int mask = this.mask;

        final int capacity = this.capacity;

        // Push keys until an empty bucket is found.
        K evictedKey;
        V evictedValue;
        int i = 0, pushIterations = this.pushIterations;
        do {
            // Replace the key and value for one of the hashes.
            switch (random.nextInt(3)) {
                case 0:
                    evictedKey = key1;
                    evictedValue = valueTable[index1];
                    keyTable[index1] = insertKey;
                    valueTable[index1] = insertValue;
                    break;
                case 1:
                    evictedKey = key2;
                    evictedValue = valueTable[index2];
                    keyTable[index2] = insertKey;
                    valueTable[index2] = insertValue;
                    break;
                default:
                    evictedKey = key3;
                    evictedValue = valueTable[index3];
                    keyTable[index3] = insertKey;
                    valueTable[index3] = insertValue;
                    break;
            }

            // If the evicted key hashes to an empty bucket, put it there and stop.
            int hashCode = evictedKey.hashCode();
            index1 = hashCode & mask;
            key1 = keyTable[index1];
            if (key1 == null) {
                keyTable[index1] = evictedKey;
                valueTable[index1] = evictedValue;
                if (size++ >= threshold) resize(capacity << 1);
                return;
            }

            index2 = hash2(hashCode);
            key2 = keyTable[index2];
            if (key2 == null) {
                keyTable[index2] = evictedKey;
                valueTable[index2] = evictedValue;
                if (size++ >= threshold) resize(capacity << 1);
                return;
            }

            index3 = hash3(hashCode);
            key3 = keyTable[index3];
            if (key3 == null) {
                keyTable[index3] = evictedKey;
                valueTable[index3] = evictedValue;
                if (size++ >= threshold) resize(capacity << 1);
                return;
            }

            if (++i == pushIterations) break;

            insertKey = evictedKey;
            insertValue = evictedValue;
        } while (true);

        putStash(evictedKey, evictedValue);
    }

    private void putStash (final K key, final V value) {
        if (stashSize == stashCapacity) {
            // Too many pushes occurred and the stash is full, increase the table size.
            resize(capacity << 1);
            put_internal(key, value);
            return;
        }
        // Store key in the stash.
        int index = capacity + stashSize;
        keyTable[index] = key;
        valueTable[index] = value;
        stashSize++;
        size++;
    }

    @Override
    public V get (Object key) {
        if (key == null) return null;

        final K[] keyTable = this.keyTable;

        int hashCode = key.hashCode();
        int index = hashCode & mask;
        if (!key.equals(keyTable[index])) {
            index = hash2(hashCode);
            if (!key.equals(keyTable[index])) {
                index = hash3(hashCode);
                if (!key.equals(keyTable[index])) return getStash((K)key);
            }
        }
        return valueTable[index];
    }

    private V getStash (final K key) {
        final K[] keyTable = this.keyTable;
        final int stashSize = this.stashSize;
        for (int i = capacity, n = i + stashSize; i < n; i++)
            if (key.equals(keyTable[i])) return valueTable[i];
        return null;
    }

    /** Returns the value for the specified key, or the default value if the key is not in the map. */
    public V get (final K key, final V defaultValue) {
        int hashCode = key.hashCode();
        int index = hashCode & mask;
        final K[] keyTable = this.keyTable;

        if (!key.equals(keyTable[index])) {
            index = hash2(hashCode);
            if (!key.equals(keyTable[index])) {
                index = hash3(hashCode);
                if (!key.equals(keyTable[index])) return getStash(key, defaultValue);
            }
        }
        return valueTable[index];
    }

    private V getStash (final K key, final V defaultValue) {
        final K[] keyTable = this.keyTable;
        final int stashSize = this.stashSize;
        for (int i = capacity, n = i + stashSize; i < n; i++)
            if (key.equals(keyTable[i])) return valueTable[i];
        return defaultValue;
    }

    //TODO faster remove(key,value) when value already known

    @Override
    public V remove (Object key) {
        if (key == null)
            throw new RuntimeException("can not remover key null");

        final K[] keyTable = this.keyTable;
        final V[] valueTable = this.valueTable;

        int hashCode = key.hashCode();
        int index = hashCode & mask;
        if (key.equals(keyTable[index])) {
            keyTable[index] = null;
            V oldValue = valueTable[index];
            valueTable[index] = null;
            size--;
            return oldValue;
        }

        index = hash2(hashCode);
        if (key.equals(keyTable[index])) {
            keyTable[index] = null;
            V oldValue = valueTable[index];
            valueTable[index] = null;
            size--;
            return oldValue;
        }

        index = hash3(hashCode);
        if (key.equals(keyTable[index])) {
            keyTable[index] = null;
            V oldValue = valueTable[index];
            valueTable[index] = null;
            size--;
            return oldValue;
        }

        return removeStash((K)key);
    }

    V removeStash (final K key) {
        if (key == null)
            throw new RuntimeException("can not remover key null");

        final K[] keyTable = this.keyTable;
        final V[] valueTable = this.valueTable;
        final int stashSize = this.stashSize;

        for (int i = capacity, n = i + stashSize; i < n; i++) {
            if (key.equals(keyTable[i])) {
                V oldValue = valueTable[i];
                removeStashIndex(i);
                size--;
                return oldValue;
            }
        }
        return null;
    }

    void removeStashIndex(final int index) {
        // If the removed location was not last, move the last tuple to the removed location.
        int lastIndex = capacity + (--stashSize);

        final K[] keyTable = this.keyTable;
        final V[] valueTable = this.valueTable;

        if (index < lastIndex) {
            keyTable[index] = keyTable[lastIndex];
            valueTable[index] = valueTable[lastIndex];
            valueTable[lastIndex] = null;
        } else
            valueTable[index] = null;
    }

    /** Reduces the size of the backing arrays to be the specified capacity or less. If the capacity is already less, nothing is
     * done. If the map contains more items than the specified capacity, the next highest power of two capacity is used instead. */
    public void shrink (int maximumCapacity) {
        if (maximumCapacity < 0) throw new IllegalArgumentException("maximumCapacity must be >= 0: " + maximumCapacity);
        if (size > maximumCapacity) maximumCapacity = size;
        if (capacity <= maximumCapacity) return;
        maximumCapacity = nextPowerOfTwo(maximumCapacity);
        resize(maximumCapacity);
    }

    /** Clears the map and reduces the size of the backing arrays to be the specified capacity if they are larger. */
    public void clear (int maximumCapacity) {
        if (capacity <= maximumCapacity) {
            clear();
            return;
        }
        size = 0;
        resize(maximumCapacity);
    }

    @Override
    public void clear () {
        K[] keyTable = this.keyTable;
        V[] valueTable = this.valueTable;
        Arrays.fill(keyTable, null);
        Arrays.fill(valueTable, null);
//        for (int i = capacity + stashSize; i-- > 0;) {
//            keyTable[i] = null;
//            valueTable[i] = null;
//        }
        size = 0;
        stashSize = 0;
    }

    @Override
    public Set<K> keySet() {
        if (size() == 0) return Collections.EMPTY_SET;

        //NOTE use of arrayunenforcedset here is good for iterating but bad for checking contains()
        Set<K> s = new ArrayUnenforcedSet<>(size()); //Global.newHashSet(size());

        for (K k : keyTable)
            if (k!=null) s.add(k);
        return s;
    }

    /** Returns true if the specified value is in the map. Note this traverses the entire map and compares every value, which may be
     * an expensive operation.
     * @param identity If true, uses == to compare the specified value with values in the map. If false, uses
     *           {@link #equals(Object)}. */
    public boolean containsValue(final Object value, final boolean identity) {
        final V[] valueTable = this.valueTable;

        final int capacity = this.capacity;
        final int stashSize = this.stashSize;

        if (value == null) {
            final K[] keyTable = this.keyTable;
            for (int i = capacity + stashSize; i-- > 0;)
                if (keyTable[i] != null && valueTable[i] == null) return true;
        } else if (identity) {
            for (int i = capacity + stashSize; i-- > 0;)
                if (valueTable[i] == value) return true;
        } else {
            for (int i = capacity + stashSize; i-- > 0;)
                if (value.equals(valueTable[i])) return true;
        }
        return false;
    }

    @Override
    public boolean containsKey(final Object key) {
        final K[] keyTable = this.keyTable;

        int hashCode = key.hashCode();
        int index = hashCode & mask;
        if (!key.equals(keyTable[index])) {
            index = hash2(hashCode);
            if (!key.equals(keyTable[index])) {
                index = hash3(hashCode);
                if (!key.equals(keyTable[index])) return containsKeyStash((K)key);
            }
        }
        return true;
    }

    private boolean containsKeyStash (final K key) {
        final K[] keyTable = this.keyTable;

        final int stashSize = this.stashSize;

        for (int i = capacity, n = i + stashSize; i < n; i++)
            if (key.equals(keyTable[i])) return true;
        return false;
    }

    /** Returns the key for the specified value, or null if it is not in the map. Note this traverses the entire map and compares
     * every value, which may be an expensive operation.
     * @param identity If true, uses == to compare the specified value with values in the map. If false, uses
     *           {@link #equals(Object)}. */
    public K findKey (final Object value, final boolean identity) {

        final int capacity = this.capacity;
        final int stashSize = this.stashSize;

        V[] valueTable = this.valueTable;
        if (value == null) {
            K[] keyTable = this.keyTable;
            for (int i = capacity + stashSize; i-- > 0;)
                if (keyTable[i] != null && valueTable[i] == null) return keyTable[i];
        } else if (identity) {
            for (int i = capacity + stashSize; i-- > 0;)
                if (valueTable[i] == value) return keyTable[i];
        } else {
            for (int i = capacity + stashSize; i-- > 0;)
                if (value.equals(valueTable[i])) return keyTable[i];
        }
        return null;
    }

    /** Increases the size of the backing array to acommodate the specified number of additional items. Useful before adding many
     * items to avoid multiple backing array resizes. */
    public void ensureCapacity (int additionalCapacity) {
        int sizeNeeded = size + additionalCapacity;
        if (sizeNeeded >= threshold) resize(nextPowerOfTwo((int)(sizeNeeded / loadFactor)));
    }

    private void resize (int newSize) {
        int oldEndIndex = capacity + stashSize;

        capacity = newSize;
        threshold = (int)(newSize * loadFactor);
        mask = newSize - 1;
        hashShift = 31 - Integer.numberOfTrailingZeros(newSize);
        stashCapacity = Math.max(3, (int)Math.ceil(Math.log(newSize)) * 2);
        pushIterations = Math.max(Math.min(newSize, 8), (int)Math.sqrt(newSize) / 8);

        K[] oldKeyTable = keyTable;
        V[] oldValueTable = valueTable;

        keyTable = (K[])new Object[newSize + stashCapacity];
        valueTable = (V[])new Object[newSize + stashCapacity];

        int oldSize = size;
        size = 0;
        stashSize = 0;
        if (oldSize > 0) {
            for (int i = 0; i < oldEndIndex; i++) {
                K key = oldKeyTable[i];
                if (key != null) putResize(key, oldValueTable[i]);
            }
        }
    }

    private int hash2(int h) {
        h *= PRIME2;
        return (h ^ h >>> hashShift) & mask;
    }

    private int hash3(int h) {
        h *= PRIME3;
        return (h ^ h >>> hashShift) & mask;
    }

    public String toString () {
        if (size == 0) return "{}";
        StringBuilder buffer = new StringBuilder(32);
        buffer.append('{');
        K[] keyTable = this.keyTable;
        V[] valueTable = this.valueTable;
        int i = keyTable.length;
        while (i-- > 0) {
            K key = keyTable[i];
            if (key == null) continue;
            buffer.append(key);
            buffer.append('=');
            buffer.append(valueTable[i]);
            break;
        }
        while (i-- > 0) {
            K key = keyTable[i];
            if (key == null) continue;
            buffer.append(", ");
            buffer.append(key);
            buffer.append('=');
            buffer.append(valueTable[i]);
        }
        buffer.append('}');
        return buffer.toString();
    }

    /** Returns an iterator for the entries in the map. Remove is supported. */
    public Entries<K, V> entries () {
        return new Entries(this);
    }

    /** Returns an iterator for the values in the map. Remove is supported. */
    public Values<V> v() {
        return new Values(this);
    }

    @Override
    public Collection<V> values() {
        return Sets.newHashSet(v().iterator());
    }

    @Override
    public Set<Map.Entry<K, V>> entrySet() {
        return null;
    }

    /** Returns an iterator for the keys in the map. Remove is supported. */
    public Keys<K> keys () {
        return new Keys(this);
    }

    static public class Entry<K, V> {
        public K key;
        public V value;

        public String toString () {
            return key + "=" + value;
        }
    }

    static private class MapIterator<K, V> {
        public boolean hasNext;

        final CuckooMap<K, V> map;
        int nextIndex, currentIndex;

        public MapIterator (CuckooMap<K, V> map) {
            this.map = map;
            reset();
        }

        public void reset () {
            currentIndex = -1;
            nextIndex = -1;
            advance();
        }

        void advance () {
            hasNext = false;
            K[] keyTable = map.keyTable;
            for (int n = map.capacity + map.stashSize; ++nextIndex < n;) {
                if (keyTable[nextIndex] != null) {
                    hasNext = true;
                    break;
                }
            }
        }

        public void remove () {
            if (currentIndex < 0) throw new IllegalStateException("next must be called before remove.");
            if (currentIndex >= map.capacity) {
                map.removeStashIndex(currentIndex);
                nextIndex = currentIndex - 1;
                advance();
            } else {
                map.keyTable[currentIndex] = null;
                map.valueTable[currentIndex] = null;
            }
            currentIndex = -1;
            map.size--;
        }
    }

    static public class Entries<K, V> extends MapIterator<K, V> implements Iterable<Entry<K, V>>, Iterator<Entry<K, V>> {
        Entry<K, V> entry = new Entry();

        public Entries (CuckooMap<K, V> map) {
            super(map);
        }

        /** Note the same entry instance is returned each time this method is called. */
        @Override
        public Entry<K, V> next () {
            if (!hasNext) throw new NoSuchElementException();
            K[] keyTable = map.keyTable;
            entry.key = keyTable[nextIndex];
            entry.value = map.valueTable[nextIndex];
            currentIndex = nextIndex;
            advance();
            return entry;
        }

        @Override
        public boolean hasNext () {
            return hasNext;
        }

        @Override
        public Iterator<Entry<K, V>> iterator () {
            return this;
        }
    }

    static public class Values<V> extends MapIterator<Object, V> implements Iterable<V>, Iterator<V> {
        public Values (CuckooMap<?, V> map) {
            super((CuckooMap<Object, V>)map);
        }

        @Override
        public boolean hasNext () {
            return hasNext;
        }

        @Override
        public V next () {
            if (!hasNext) throw new NoSuchElementException();
            V value = map.valueTable[nextIndex];
            currentIndex = nextIndex;
            advance();
            return value;
        }

        @Override
        public Iterator<V> iterator () {
            return this;
        }

        //these 2 methods are dangerous to use because while loop spins on hasNext field
//        /** Returns a new array containing the remaining values. */
//        public ArrayList<V> toArray () {
//            ArrayList array = new ArrayList(map.size);
//            while (hasNext)
//                array.add(next());
//            return array;
//        }
//
//        /** Adds the remaining values to the specified array. */
//        public void toArray (List<V> array) {
//            while (hasNext)
//                array.add(next());
//        }
    }

    static public class Keys<K> extends MapIterator<K, Object> implements Iterable<K>, Iterator<K> {
        public Keys (CuckooMap<K, ?> map) {
            super((CuckooMap<K, Object>)map);
        }

        @Override
        public boolean hasNext () {
            return hasNext;
        }

        @Override
        public K next () {
            if (!hasNext) throw new NoSuchElementException();
            K key = map.keyTable[nextIndex];
            currentIndex = nextIndex;
            advance();
            return key;
        }

        @Override
        public Iterator<K> iterator () {
            return this;
        }


        /** Returns a new array containing the remaining keys. */
        /*
        //dangerous to use because while loop spins on hasNext field
        public ArrayList<K> toArray () {
            ArrayList array = new ArrayList(map.size);
            while (hasNext)
                array.add(next());
            return array;
        }
        */
    }

    static public int nextPowerOfTwo (int value) {
        if (value == 0) return 1;
        value--;
        value |= value >> 1;
        value |= value >> 2;
        value |= value >> 4;
        value |= value >> 8;
        value |= value >> 16;
        return value + 1;
    }
}