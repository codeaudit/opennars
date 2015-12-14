package nars.bag;

import nars.budget.Budget;

import java.util.Iterator;
import java.util.function.Consumer;

/**
 * Bag which holds nothing
 */
public final class NullBag<V> extends Bag<V> {
    @Override
    public void clear() {

    }

    @Override
    public BagBudget<V> get(V key) {
        return null;
    }

    @Override
    public V peekNext() {
        return null;
    }

    @Override
    public BagBudget<V> remove(V key) {
        return null;
    }

    @Override
    public BagBudget<V> put(V newItem) {
        return null;
    }

    @Override
    public BagBudget<V> put(V k, Budget b) {
        return null;
    }

    @Override
    public BagBudget<V> put(V v, BagBudget<V> vBagBudget) {
        return null;
    }


    @Override
    public int capacity() {
        return 0;
    }

    @Override
    public V pop() {
        return null;
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public Iterator<V> iterator() {
        return null;
    }

    @Override
    public void forEachEntry(Consumer<BagBudget> each) {

    }

    @Override
    public void setCapacity(int c) {

    }
}
