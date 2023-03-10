/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.cloud.teleport.templates.common;

import com.google.api.services.bigquery.model.TableRow;
import com.google.auto.value.AutoValue;
import com.google.privacy.dlp.v2.DeidentifyContentResponse;
import com.google.privacy.dlp.v2.InspectContentResponse;
import com.google.privacy.dlp.v2.ReidentifyContentResponse;
import com.google.privacy.dlp.v2.Table;
// import com.google.cloud.teleport.templates.beam.DLPInspectText;
import com.google.cloud.teleport.beam.DLPReidentifyText;
import com.google.cloud.teleport.templates.common.Util.DLPMethod;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.beam.sdk.options.ValueProvider;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryHelpers;
import org.apache.beam.sdk.metrics.Counter;
import org.apache.beam.sdk.metrics.Metrics;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PCollectionTuple;
import org.apache.beam.sdk.values.PCollectionView;
import org.apache.beam.sdk.values.Row;
import org.apache.beam.sdk.values.TupleTagList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@AutoValue
public abstract class DLPTransform
    extends PTransform<PCollection<KV<String, Table.Row>>, PCollectionTuple> {

  public static final Logger LOG = LoggerFactory.getLogger(DLPTransform.class);

  // @Nullable
  // public abstract ValueProvider<String> inspectTemplateName();

  @Nullable
  public abstract ValueProvider<String> deidTemplateName();

  // public abstract ValueProvider<Integer> batchSize();
  abstract ValueProvider<Integer> batchSize();

  public abstract String projectId();

  public abstract ValueProvider<Character> columnDelimiter();

  // public abstract ValueProvider<DLPMethod> dlpmethod();

  public abstract String jobName();

  public abstract PCollectionView<Map<String, List<String>>> headers();

  @AutoValue.Builder
  public abstract static class Builder {

    // public abstract Builder setInspectTemplateName(ValueProvider<String>
    // inspectTemplateName);

    public abstract Builder setDeidTemplateName(ValueProvider<String> inspectTemplateName);

    public abstract Builder setBatchSize(ValueProvider<Integer> batchSize);

    public abstract Builder setProjectId(String projectId);

    public abstract Builder setHeaders(PCollectionView<Map<String, List<String>>> headers);

    public abstract Builder setColumnDelimiter(ValueProvider<Character> columnDelimiter);

    // public abstract Builder setDlpmethod(ValueProvider<DLPMethod> method);

    public abstract Builder setJobName(String jobName);

    public abstract DLPTransform build();
  }

  public static Builder newBuilder() {
    return new AutoValue_DLPTransform.Builder();
  }

  @Override
  public PCollectionTuple expand(PCollection<KV<String, Table.Row>> input) {
    return input
        .apply(
            "ReIdTransform",
            DLPReidentifyText.newBuilder()
                .setBatchSizeBytes(batchSize())
                .setColumnDelimiter(columnDelimiter())
                .setHeaderColumns(headers())
                // .setInspectTemplateName(inspectTemplateName())
                .setReidentifyTemplateName(deidTemplateName())
                .setProjectId(projectId())
                .build())
        .apply(
            "ConvertReidResponse",
            ParDo.of(new ConvertReidResponse())
                .withOutputTags(Util.reidSuccess, TupleTagList.of(Util.reidFailure)));
  }

  static class ConvertReidResponse
      extends DoFn<KV<String, ReidentifyContentResponse>, KV<String, TableRow>> {

    private final Counter numberOfBytesReidentified = Metrics.counter(ConvertReidResponse.class,
        "NumberOfBytesReidentified");

    @ProcessElement
    public void processElement(
        @Element KV<String, ReidentifyContentResponse> element, MultiOutputReceiver out) {

      String deidTableName = BigQueryHelpers.parseTableSpec(element.getKey()).getTableId();
      String tableName = String.format("%s_%s", deidTableName, Util.BQ_REID_TABLE_EXT);
//      LOG.info("Table Ref {}", tableName);
      Table originalData = element.getValue().getItem().getTable();
      numberOfBytesReidentified.inc(originalData.toByteArray().length);
      List<String> headers = originalData.getHeadersList().stream()
          .map(fid -> fid.getName())
          .collect(Collectors.toList());
      List<Table.Row> outputRows = originalData.getRowsList();
      if (outputRows.size() > 0) {
        for (Table.Row outputRow : outputRows) {
          if (outputRow.getValuesCount() != headers.size()) {
            throw new IllegalArgumentException(
                "BigQuery column count must exactly match with data element count");
          }
          out.get(Util.reidSuccess)
              .output(
                  KV.of(
                      tableName,
                      Util.createBqRow(outputRow, headers.toArray(new String[headers.size()]))));
        }
      }
    }
  }
}
