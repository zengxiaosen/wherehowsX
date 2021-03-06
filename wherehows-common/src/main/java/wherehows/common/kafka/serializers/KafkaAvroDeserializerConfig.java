/**
 * Copyright 2015 LinkedIn Corp. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 */

package wherehows.common.kafka.serializers;

import io.confluent.common.config.ConfigDef;
import io.confluent.common.config.ConfigDef.Type;
import io.confluent.common.config.ConfigDef.Importance;

import java.util.Map;

public class KafkaAvroDeserializerConfig extends AbstractKafkaAvroSerDeConfig {

  public static final String SPECIFIC_AVRO_READER_CONFIG = "specific.avro.reader";
  public static final boolean SPECIFIC_AVRO_READER_DEFAULT = false;
  public static final String SPECIFIC_AVRO_READER_DOC =
      "If true, tries to look up the SpecificRecord class ";

  private static ConfigDef config;

  static {
    config = baseConfigDef()
        .define(SPECIFIC_AVRO_READER_CONFIG, Type.BOOLEAN, SPECIFIC_AVRO_READER_DEFAULT,
                Importance.LOW, SPECIFIC_AVRO_READER_DOC);
  }

  public KafkaAvroDeserializerConfig(Map<?, ?> props) {
    super(config, props);
  }
}
