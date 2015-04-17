package nars.budget.bag;

import com.google.common.cache.*;
import nars.Events;
import nars.Memory;
import nars.nal.Concept;
import nars.nal.Item;

/**
 * Index of stored Items (ex: concepts) which is optimized for
 * random access storage and retrieval and not prioritized active processing.
 * not really a bag, maybe it should be called ItemCache
 * 
 * http://docs.guava-libraries.googlecode.com/git-history/release/javadoc/com/google/common/cache/package-summary.html*
 */
public class CacheBag<K, I extends Item<K>> implements Memory.MemoryAware, RemovalListener<K,I> {

    public final Cache<K, I> data;
    private Memory memory;

    public CacheBag(int capacity) {
        
       data = CacheBuilder.newBuilder()
            .maximumSize(capacity)
            //.expireAfterWrite(10, TimeUnit.MINUTES)
               /*.weakKeys()
               .weakValues()
               .weigher(null)*/
        .removalListener(this)
        .build();
    }


    /** empty contents */
    public void clear() {
        data.invalidateAll();
        data.cleanUp();
    }


    public I get(K key) {
        return data.getIfPresent(key);
    }
    
    public I take(K key) {
        I i = data.getIfPresent(key);
        if (i!=null) {
            data.invalidate(i);
            data.cleanUp();
            return i;
        }
        return null;
    }
    
    public void add(I i) {
        data.put(i.name(), i);
    }

    public long size() {
        return data.size();
    }

    @Override
    public void onRemoval(RemovalNotification<K, I> rn) {
        if (rn.getCause()==RemovalCause.SIZE) {
            if (memory!=null) {
                Object v = rn.getValue();
                if (v instanceof Concept)
                    memory.emit(Events.ConceptForget.class, v);
            }
            I v = rn.getValue();
            if (v!=null) v.end();
        }
    }


    @Override
    public void setMemory(Memory m) {
        this.memory = m;
    }
}