package com.cachelab.server.controller;

import com.cachelab.server.dto.CompareRequest;
import com.cachelab.server.dto.CompareResponse;
import com.cachelab.server.dto.RunConfigRequest;
import com.cachelab.server.dto.RunResponse;
import com.cachelab.server.model.Sample;
import com.cachelab.server.service.Run;
import com.cachelab.server.service.RunManager;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class RunController {

    private final RunManager runManager;

    public RunController(RunManager runManager) {
        this.runManager = runManager;
    }

    @PostMapping("/runs")
    public ResponseEntity<RunResponse> startRun(@Valid @RequestBody RunConfigRequest request) {
        String runId = runManager.startRun(request.toDomain());
        return ResponseEntity.status(HttpStatus.CREATED).body(new RunResponse(runId));
    }

    @GetMapping(value = "/runs/{runId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamRun(@PathVariable String runId) {
        Run run = runManager.getRun(runId);
        if (run == null) {
            SseEmitter errorEmitter = new SseEmitter(0L);
            errorEmitter.completeWithError(new IllegalArgumentException("Run not found: " + runId));
            return errorEmitter;
        }

        SseEmitter emitter = new SseEmitter(0L);
        run.addEmitter(emitter);
        return emitter;
    }

    @PostMapping("/runs/{runId}/stop")
    public ResponseEntity<Void> stopRun(@PathVariable String runId) {
        runManager.stopRun(runId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/runs/{runId}")
    public ResponseEntity<Sample> getRunSample(@PathVariable String runId) {
        Run run = runManager.getRun(runId);
        if (run == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(run.getLatestSample());
    }

    @PostMapping("/compare")
    public ResponseEntity<CompareResponse> startCompare(@Valid @RequestBody CompareRequest request) {
        Map<String, String> runIds = runManager.startCompare(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new CompareResponse(runIds));
    }
}
