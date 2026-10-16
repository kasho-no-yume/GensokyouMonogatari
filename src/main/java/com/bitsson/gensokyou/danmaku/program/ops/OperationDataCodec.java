package com.bitsson.gensokyou.danmaku.program.ops;

import java.lang.reflect.*;
import java.util.*;

/** Canonical bounded record data. Only explicitly installed record constructors can be invoked.
 * Wire data never contains a Java class name. Record constructors enforce semantic validation.
 * This is an operations-local codec, not a modification of the program/network envelope. */
public final class OperationDataCodec {
    private final Map<String,Class<? extends Record>> records=new LinkedHashMap<>();
    private final Map<Class<?>,String> identities=new HashMap<>();
    private boolean frozen;

    public OperationDataCodec register(String id,Class<? extends Record> type) {
        if (frozen) throw new IllegalStateException("codec registry frozen");
        if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || records.containsKey(id) || identities.containsKey(type)) throw new IllegalArgumentException("duplicate/invalid record type");
        records.put(id,type); identities.put(type,id); return this;
    }
    public OperationDataCodec freeze() { frozen=true; return this; }
    public Map<String,String> encode(Record value) {
        if (!frozen) throw new IllegalStateException("freeze codec registry first");
        var fields=new TreeMap<String,String>(); write(fields,"v",value,0); return Map.copyOf(fields);
    }
    public <T> T decode(Map<String,String> fields,Class<T> expected) {
        if (!frozen) throw new IllegalStateException("freeze codec registry first");
        if (fields.size()>4096) throw new IllegalArgumentException("operation field limit");
        for (var field:fields.entrySet()) if (field.getKey().length()>256 || field.getValue().length()>256) throw new IllegalArgumentException("operation string limit");
        Object value=read(fields,"v",expected,0);
        if (!(value instanceof Record record) || !encode(record).equals(fields)) throw new IllegalArgumentException("noncanonical operation data");
        return expected.cast(value);
    }
    private static void guard(int depth,int count) { if (depth>16 || count>4096) throw new IllegalArgumentException("operation depth/field limit"); }
    private void write(Map<String,String> out,String key,Object value,int depth) {
        guard(depth,out.size());
        if (value instanceof Paths.Segments segments) value=OperationLibrary.PathSpec.segments(segments.segments(),segments.tail());
        if (value==null) { out.put(key,"null"); return; }
        if (value instanceof Record record) {
            String id=identities.get(record.getClass());
            if (id==null) throw new IllegalArgumentException("unregistered operation data "+record.getClass());
            out.put(key,id);
            try { for (RecordComponent field:record.getClass().getRecordComponents()) write(out,key+"."+field.getName(),field.getAccessor().invoke(record),depth+1); }
            catch (ReflectiveOperationException e) { throw new IllegalArgumentException("record access failed",e); }
        } else if (value instanceof Collection<?> collection) {
            if (collection.size()>512) throw new IllegalArgumentException("collection limit");
            List<?> ordered=value instanceof Set<?> ? collection.stream().sorted(Comparator.comparing(OperationDataCodec::scalar)).toList() : new ArrayList<>(collection);
            out.put(key,Integer.toString(ordered.size()));
            for (int i=0;i<ordered.size();i++) write(out,key+"."+i,ordered.get(i),depth+1);
        } else out.put(key,scalar(value));
        guard(depth,out.size());
        if (key.length()>256 || out.get(key).length()>256) throw new IllegalArgumentException("operation string limit");
    }
    private static String scalar(Object value) {
        if (value instanceof Double number) { if (!Double.isFinite(number)) throw new IllegalArgumentException("finite scalar required"); return Double.toHexString(number); }
        if (value instanceof String || value instanceof Integer || value instanceof Long || value instanceof Boolean || value instanceof UUID) return value.toString();
        if (value instanceof Enum<?> enumeration) return enumeration.name();
        throw new IllegalArgumentException("unsupported operation scalar");
    }
    private Object read(Map<String,String> fields,String key,Type expected,int depth) {
        guard(depth,fields.size());
        String value=fields.get(key);
        if (value==null) throw new IllegalArgumentException("missing operation field "+key);
        if (expected instanceof ParameterizedType generic) {
            if (generic.getRawType()!=List.class && generic.getRawType()!=Set.class) throw new IllegalArgumentException("unsupported collection type");
            int count=Integer.parseInt(value);
            if (count<0 || count>512) throw new IllegalArgumentException("collection limit");
            var result=new ArrayList<>();
            for (int i=0;i<count;i++) result.add(read(fields,key+"."+i,generic.getActualTypeArguments()[0],depth+1));
            return generic.getRawType()==Set.class ? Set.copyOf(result) : List.copyOf(result);
        }
        Class<?> type=(Class<?>)expected;
        if (value.equals("null") && type!=String.class) {
            if (type.isPrimitive()) throw new IllegalArgumentException("null primitive");
            return null;
        }
        if (type==String.class) return value;
        if (type==double.class || type==Double.class) return Double.valueOf(value);
        if (type==int.class || type==Integer.class) return Integer.valueOf(value);
        if (type==long.class || type==Long.class) return Long.valueOf(value);
        if (type==boolean.class || type==Boolean.class) { if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException("invalid boolean"); return Boolean.valueOf(value); }
        if (type==UUID.class) return UUID.fromString(value);
        if (type.isEnum()) return enumValue(type,value);
        Class<? extends Record> installed=records.get(value);
        boolean segmentAdapter=installed==OperationLibrary.PathSpec.class && Path.class.isAssignableFrom(type);
        if (installed==null || !type.isAssignableFrom(installed) && !segmentAdapter) throw new IllegalArgumentException("unknown/incompatible operation record "+value);
        RecordComponent[] components=installed.getRecordComponents();
        Object[] arguments=new Object[components.length]; Class<?>[] signature=new Class<?>[components.length];
        for (int i=0;i<components.length;i++) { signature[i]=components[i].getType(); arguments[i]=read(fields,key+"."+components[i].getName(),components[i].getGenericType(),depth+1); }
        try {
            Object decoded=installed.getDeclaredConstructor(signature).newInstance(arguments);
            return segmentAdapter?((OperationLibrary.PathSpec)decoded).evaluator():decoded;
        }
        catch (InvocationTargetException e) { throw new IllegalArgumentException("invalid "+value,e.getCause()); }
        catch (ReflectiveOperationException e) { throw new IllegalArgumentException("installed record constructor unavailable",e); }
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    private static Object enumValue(Class<?> type,String value) { return Enum.valueOf((Class)type,value); }
}
