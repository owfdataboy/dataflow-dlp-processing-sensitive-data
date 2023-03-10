gcloud dataflow flex-template build gs://dataflow-dlp/templates/batching-beam-deidentify-dlp.json \
    --image-gcr-path "asia-southeast1-docker.pkg.dev/ext-pinetree/dataflow-dlp-images/dataflow/batching-beam-deidentify-dlp:latest" \
    --sdk-language "JAVA" \
    --flex-template-base-image JAVA11 \
    --metadata-file "v1/target/classes/batch-dlp-gcs-text-to-bigquery-generated-metadata.json" \
    --jar "v1/target/classic-templates-1.0-SNAPSHOT.jar" \
    --env FLEX_TEMPLATE_JAVA_MAIN_CLASS="com.google.cloud.teleport.templates.custom.DLPDeidentifyGCSToBigQueryBatch"

# gcloud dataflow flex-template build gs://ext-pinetree-re-identify-test/templates/batching-beam-reidentify-dlp.json \
#     --image-gcr-path "asia-southeast1-docker.pkg.dev/ext-pinetree/dataflow-dlp-images/dataflow/batching-beam-reidentify-dlp:latest" \
#     --sdk-language "JAVA" \
#     --flex-template-base-image JAVA11 \
#     --metadata-file "v1/target/classes/batch-dlp-bigquery-to-bigquery-reidentify-generated-metadata.json" \
#     --jar "v1/target/classic-templates-1.0-SNAPSHOT.jar" \
#     --env FLEX_TEMPLATE_JAVA_MAIN_CLASS="com.google.cloud.teleport.templates.custom.DLPReidentifyBigQueryToBigQueryBatch"