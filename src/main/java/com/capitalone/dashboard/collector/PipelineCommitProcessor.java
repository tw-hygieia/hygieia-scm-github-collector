package com.capitalone.dashboard.collector;

import com.capitalone.dashboard.model.*;
import com.capitalone.dashboard.repository.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@org.springframework.stereotype.Component
public class PipelineCommitProcessor {
    private static final Log LOG = LogFactory.getLog(PipelineCommitProcessor.class);
    private final BaseCollectorRepository<Collector> collectorRepository;
    private final CollectorItemRepository collectorItemRepository;
    private final PipelineRepository pipelineRepository;
    private final ComponentRepository componentRepository;
    private final DashboardRepository dashboardRepository;

    @Autowired
    public PipelineCommitProcessor(BaseCollectorRepository<Collector> collectorRepository, CollectorItemRepository collectorItemRepository, PipelineRepository pipelineRepository, ComponentRepository componentRepository, DashboardRepository dashboardRepository) {
        this.collectorRepository = collectorRepository;
        this.collectorItemRepository = collectorItemRepository;
        this.pipelineRepository = pipelineRepository;
        this.componentRepository = componentRepository;
        this.dashboardRepository = dashboardRepository;
    }


    public void processPipelineCommits(List<Commit> commits) {
        List<Commit> commitsToConsider = commits;
        if (commitsToConsider.isEmpty()) {
            LOG.info("No commits to be added on the pipeline collection during this scheduled run...");
            return;
        }
        List<Dashboard> allDashboardsForCommit = findAllDashboardsForCommit(commitsToConsider.get(0));
        String environmentName = PipelineStage.COMMIT.getName();
        List<Collector> collectorList = collectorRepository.findByCollectorType(CollectorType.Product);
        List<CollectorItem> collectorItemList = collectorItemRepository.findByCollectorIdIn(collectorList.stream().map(BaseModel::getId).collect(Collectors.toList()));

        for (CollectorItem collectorItem : collectorItemList) {
            List<String> dashBoardIds = allDashboardsForCommit.stream().map(d -> d.getId().toString()).collect(Collectors.toList());
            boolean dashboardId = dashBoardIds.contains(collectorItem.getOptions().get("dashboardId").toString());
            if(dashboardId) {
                Pipeline pipeline = getOrCreatePipeline(collectorItem);

                Map<String, EnvironmentStage> environmentStageMap = pipeline.getEnvironmentStageMap();
                if (environmentStageMap.get(environmentName) == null) {
                    environmentStageMap.put(environmentName, new EnvironmentStage());
                }

                EnvironmentStage environmentStage = environmentStageMap.get(environmentName);
                if(environmentStage.getCommits() == null) {
                    environmentStage.setCommits(new HashSet<>());
                }
                environmentStage.getCommits().addAll(commitsToConsider.stream()
                        .map(commit -> new PipelineCommit(commit, commit.getTimestamp())).collect(Collectors.toSet()));
                pipelineRepository.save(pipeline);
            }
        }
    }

    private List<Dashboard> findAllDashboardsForCommit(Commit commit){
        if (commit.getCollectorItemId() == null) return new ArrayList<>();
        CollectorItem commitCollectorItem = collectorItemRepository.findOne(commit.getCollectorItemId());
        List<com.capitalone.dashboard.model.Component> components = componentRepository.findBySCMCollectorItemId(commitCollectorItem.getId());
        List<ObjectId> componentIds = components.stream().map(BaseModel::getId).collect(Collectors.toList());
        return dashboardRepository.findByApplicationComponentIdsIn(componentIds);
    }

    protected Pipeline getOrCreatePipeline(CollectorItem collectorItem) {
        Pipeline pipeline = pipelineRepository.findByCollectorItemId(collectorItem.getId());
        if(pipeline == null){
            pipeline = new Pipeline();
            pipeline.setCollectorItemId(collectorItem.getId());
        }
        return pipeline;
    }
}
