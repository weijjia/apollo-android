package com.apollographql.apollo.internal.field;

import com.apollographql.apollo.api.Field;
import com.apollographql.apollo.api.Operation;
import com.apollographql.apollo.cache.normalized.CacheReference;
import com.apollographql.apollo.internal.cache.normalized.ReadableCache;
import com.apollographql.apollo.cache.normalized.Record;

import java.util.ArrayList;
import java.util.List;

public final class CacheFieldValueResolver implements FieldValueResolver<Record> {
  private final ReadableCache readableCache;
  private final Operation.Variables variables;

  public CacheFieldValueResolver(ReadableCache readableCache, Operation.Variables variables) {
    this.readableCache = readableCache;
    this.variables = variables;
  }

  @SuppressWarnings("unchecked") @Override public <T> T valueFor(Record record, Field field) {
    if (field instanceof Field.ObjectField) {
      return (T) valueFor(record, (Field.ObjectField) field);
    } else if (field instanceof Field.ScalarListField) {
      return fieldValue(record, field);
    } else if (field instanceof Field.ObjectListField) {
      return (T) valueFor(record, (Field.ObjectListField) field);
    } else {
      return fieldValue(record, field);
    }
  }

  private Record valueFor(Record record, Field.ObjectField field) {
    CacheReference cacheReference = fieldValue(record, field);
    return cacheReference != null ? readableCache.read(cacheReference.key()) : null;
  }

  private List<Record> valueFor(Record record, Field.ObjectListField field) {
    List<CacheReference> values = fieldValue(record, field);
    List<Record> result = new ArrayList<>();
    for (CacheReference reference : values) {
      result.add(readableCache.read(reference.key()));
    }
    return result;
  }

  @SuppressWarnings("unchecked") private <T> T fieldValue(Record record, Field field) {
    return (T) record.field(field.cacheKey(variables));
  }
}
