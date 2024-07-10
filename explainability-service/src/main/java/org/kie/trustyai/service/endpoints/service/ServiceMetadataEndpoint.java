package org.kie.trustyai.service.endpoints.service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.jboss.logging.Logger;
import org.kie.trustyai.explainability.model.dataframe.Dataframe;
import org.kie.trustyai.service.config.metrics.MetricsConfig;
import org.kie.trustyai.service.data.datasources.DataSource;
import org.kie.trustyai.service.data.exceptions.DataframeCreateException;
import org.kie.trustyai.service.data.exceptions.StorageReadException;
import org.kie.trustyai.service.data.metadata.StorageMetadata;
import org.kie.trustyai.service.payloads.metrics.BaseMetricRequest;
import org.kie.trustyai.service.payloads.service.DataTagging;
import org.kie.trustyai.service.payloads.service.NameMapping;
import org.kie.trustyai.service.payloads.service.ServiceMetadata;
import org.kie.trustyai.service.payloads.service.*;
import org.kie.trustyai.service.prometheus.PrometheusScheduler;
import org.kie.trustyai.service.validators.generic.GenericValidationUtils;
import org.kie.trustyai.service.validators.serviceRequests.ValidNameMappingRequest;

import com.fasterxml.jackson.core.JsonProcessingException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
@Path("/info")
public class ServiceMetadataEndpoint {

    private static final Logger LOG = Logger.getLogger(ServiceMetadataEndpoint.class);
    @Inject
    Instance<DataSource> dataSource;

    @Inject
    PrometheusScheduler scheduler;

    @Inject
    MetricsConfig metricsConfig;

    ServiceMetadataEndpoint() {

    }

    private List<ServiceMetadata> getServiceMetadata(boolean loadColumnValues) {
        final List<ServiceMetadata> serviceMetadataList = new ArrayList<>();
        for (String modelId : dataSource.get().getKnownModels()) {
            final ServiceMetadata serviceMetadata = new ServiceMetadata();

            for (Map.Entry<String, ConcurrentHashMap<UUID, BaseMetricRequest>> metricDict : scheduler.getAllRequests()
                    .entrySet()) {
                metricDict.getValue().values().forEach(metric -> {
                    if (metric.getModelId().equals(modelId)) {
                        final String metricName = metricDict.getKey();
                        serviceMetadata.getMetrics().scheduledMetadata.setCount(metricName,
                                serviceMetadata.getMetrics().scheduledMetadata.getCount(metricName) + 1);
                    }
                });
            }

            try {
                final StorageMetadata storageMetadata = dataSource.get().getMetadata(modelId, loadColumnValues);
                serviceMetadata.setData(storageMetadata);
            } catch (DataframeCreateException | StorageReadException | NullPointerException e) {
                LOG.warn("Problem creating dataframe: " + e.getMessage(), e);
            }

            serviceMetadataList.add(serviceMetadata);
        }
        return serviceMetadataList;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response serviceInfo() throws JsonProcessingException {
        return Response.ok(getServiceMetadata(false)).build();
    }

    @GET
    @Path("/values")
    @Produces(MediaType.APPLICATION_JSON)
    public Response serviceInfoWithValues() throws JsonProcessingException {
        return Response.ok(getServiceMetadata(true)).build();
    }

    @GET
    @Path("/tags")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTagInformation() {
        Map<String, Map<String, Long>> perModelTagCounts = new HashMap<>();
        for (String modelId : dataSource.get().getVerifiedModels()) {
            List<String> tags = dataSource.get().getTags(modelId);
            Map<String, Long> tagCounts = tags.stream().collect(Collectors.groupingBy(s -> s, Collectors.counting()));
            perModelTagCounts.put(modelId, tagCounts);
        }
        return Response.ok(perModelTagCounts).build();
    }

    @POST
    @Path("/tags")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response labelSchema(DataTagging dataTagging) throws JsonProcessingException {

        if (!dataSource.get().getKnownModels().contains(dataTagging.getModelId())) {
            return Response.serverError()
                    .status(Response.Status.BAD_REQUEST)
                    .entity("No metadata found for model=" + dataTagging.getModelId() + ". This can happen if TrustyAI has not yet logged any inferences from this model.")
                    .build();
        }

        try {
            HashMap<String, List<List<Integer>>> tagMapping = new HashMap<>();
            List<String> tagErrors = new ArrayList<>();
            for (String tag : dataTagging.getDataTagging().keySet()) {
                Optional<String> tagValidationErrorMessage = GenericValidationUtils.validateDataTag(tag);
                tagValidationErrorMessage.ifPresent(tagErrors::add);
                tagMapping.put(tag, dataTagging.getDataTagging().get(tag));
            }

            if (!tagErrors.isEmpty()) {
                return Response.serverError().entity(String.join(", ", tagErrors)).status(Response.Status.BAD_REQUEST).build();
            }

            dataSource.get().tagDataframeRows(dataTagging);
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            return Response.serverError()
                    .status(Response.Status.BAD_REQUEST)
                    .entity(e.getMessage())
                    .build();
        }

        return Response.ok().entity("Datapoints successfully tagged.").build();
    }

    @POST
    @Path("/names")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response applyNameMappings(@ValidNameMappingRequest NameMapping nameMapping) {

        if (!dataSource.get().getKnownModels().contains(nameMapping.getModelId())) {
            return Response.serverError()
                    .status(Response.Status.BAD_REQUEST)
                    .entity("No metadata found for model=" + nameMapping.getModelId() + ". This can happen if TrustyAI has not yet logged any inferences from this model.")
                    .build();
        }

        dataSource.get().applyNameMapping(nameMapping);

        LOG.info("Name mappings successfully applied to model=" + nameMapping.getModelId() + ".");
        return Response.ok().entity("Feature and output name mapping successfully applied.").build();
    }

    @DELETE
    @Path("/names")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response clearNameMappings(String modelId) {

        if (!dataSource.get().getKnownModels().contains(modelId)) {
            return Response.serverError()
                    .status(Response.Status.BAD_REQUEST)
                    .entity("No metadata found for model=" + modelId + ". This can happen if TrustyAI has not yet logged any inferences from this model.")
                    .build();
        }

        dataSource.get().clearNameMapping(modelId);

        LOG.info("Name mappings successfully cleared from model=" + modelId + ".");
        return Response.ok().entity("Feature and output name mapping successfully cleared.").build();
    }

    @GET
    @Path("/inference/ids/{model}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get model's inference ids", description = "Get all the inference ids for a given model")
    public Response inferenceIdsByModel(@Parameter(description = "The model to get inference ids from", required = true) @PathParam("model") String model,
            @Parameter(description = "The type of inferences to retrieve", required = false) @QueryParam("type") @DefaultValue("all") String type) {
        try {

            final Dataframe df;
            if ("organic".equalsIgnoreCase(type)) {
                df = dataSource.get().getOrganicDataframe(model);

            } else if ("all".equalsIgnoreCase(type)) {
                df = dataSource.get().getDataframe(model);
            } else {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("Invalid type parameter. Valid values must be in ['organic', 'all'].")
                        .build();
            }
            final List<LocalDateTime> timestamps = df.getTimestamps();
            final List<String> ids = df.getIds();

            return Response.ok().entity(IntStream.range(0, df.getRowDimension())
                    .mapToObj(row -> new InferenceId(ids.get(row), timestamps.get(row)))
                    .collect(Collectors.toUnmodifiableList())).build();
        } catch (DataframeCreateException e) {
            return Response.serverError()
                    .status(Response.Status.BAD_REQUEST)
                    .entity("Model ID " + model + " does not exist in TrustyAI metadata.")
                    .build();

        }
    }

}
