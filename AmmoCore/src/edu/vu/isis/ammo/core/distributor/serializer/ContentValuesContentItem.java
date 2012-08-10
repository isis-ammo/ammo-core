package edu.vu.isis.ammo.core.distributor.serializer;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.ContentValues;

import edu.vu.isis.ammo.core.distributor.ContractStore;
import edu.vu.isis.ammo.core.distributor.RequestSerializer.FieldType;

public class ContentValuesContentItem implements IContentItem {
    static final Logger logger = LoggerFactory.getLogger("dist.serializer.cursoritem");
    
    private final ContentValues cv;
    private final Map<String, FieldType> fieldTypes;
    
    public ContentValuesContentItem(ContentValues cv, ContractStore.Relation relation) {
        this.cv = cv;
        
        fieldTypes = new HashMap<String, FieldType>(cv.size());
        
        for (ContractStore.Field f : relation.getFields()) {
            fieldTypes.put(f.getName().getSnake(), FieldType.fromContractString(f.getDtype()));
        }
    }

    @Override
    public void close() {
        //don't have anything to close
    }

    @Override
    public Set<String> keySet() {
        return fieldTypes.keySet();
    }

    @Override
    public FieldType getTypeForKey(String key) {
        return fieldTypes.get(key);
    }

    @Override
    public Object get(String key) {
        return cv.get(key);
    }

    @Override
    public Boolean getAsBoolean(String key) {
        return cv.getAsBoolean(key);
    }

    @Override
    public Byte getAsByte(String key) {
        return cv.getAsByte(key);
    }

    @Override
    public byte[] getAsByteArray(String key) {
        return cv.getAsByteArray(key);
    }

    @Override
    public Double getAsDouble(String key) {
        return cv.getAsDouble(key);
    }

    @Override
    public Float getAsFloat(String key) {
        return cv.getAsFloat(key);
    }

    @Override
    public Integer getAsInteger(String key) {
        return cv.getAsInteger(key);
    }

    @Override
    public Long getAsLong(String key) {
        return cv.getAsLong(key);
    }

    @Override
    public Short getAsShort(String key) {
        return cv.getAsShort(key);
    }

    @Override
    public String getAsString(String key) {
        return cv.getAsString(key);
    }

}
