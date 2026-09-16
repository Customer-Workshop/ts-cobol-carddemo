package com.carddemo.api;

import com.carddemo.model.PendingAuthDetail;
import com.carddemo.repository.PendingAuthDetailRepository;
import com.carddemo.service.PendingAuthDetailService;
import com.carddemo.service.PendingAuthService;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * REST surface for the pending-auth screens (FR-S19 §3.3):
 * {@code GET /api/pending-auth/{acctId}} is the fresh list entry —
 * context + summary + the first page; {@code /details?after=&dir=} pages
 * the PAUTDTL1 keyset; {@code /details/{authKey}} is the COPAU1A field
 * set; {@code POST .../fraud} toggles the fraud flag and journals through
 * the AUTHFRDS upsert.
 */
@RestController
@RequestMapping("/api/pending-auth")
public class PendingAuthController {
    private final PendingAuthService pendingAuthService;
    private final PendingAuthDetailService detailService;
    private final PendingAuthDetailRepository detailRepository;

    public PendingAuthController(PendingAuthService pendingAuthService,
                                 PendingAuthDetailService detailService,
                                 PendingAuthDetailRepository detailRepository) {
        this.pendingAuthService = pendingAuthService;
        this.detailService = detailService;
        this.detailRepository = detailRepository;
    }

    @GetMapping("/{acctId}")
    public PendingAuthResponse list(@PathVariable Long acctId) {
        return pendingAuthService.browse(
                new PendingAuthRequest("ENTER", String.valueOf(acctId), null, null));
    }

    // Keyset page: 'fwd' continues strictly after the cursor, 'bwd' returns
    // the page immediately before it (S19-B8).
    @GetMapping("/{acctId}/details")
    public PendingAuthResponse details(@PathVariable Long acctId,
                                       @RequestParam(required = false) String after,
                                       @RequestParam(defaultValue = "fwd") String dir) {
        PendingAuthKey key = PendingAuthKey.parse(after);
        if ("bwd".equals(dir) && key != null) {
            return backwardPage(acctId, key);
        }
        return pendingAuthService.browse(key == null
                ? new PendingAuthRequest("ENTER", String.valueOf(acctId), null, null)
                : new PendingAuthRequest("PF8", null, null,
                        new PendingAuthPageState(acctId, 1, List.of(), after, true,
                                new ArrayList<>(
                                        Collections.nCopies(PendingAuthPageState.ROW_COUNT, null)))));
    }

    @GetMapping("/{acctId}/details/{authKey}")
    public PendingAuthDetailScreen detail(@PathVariable Long acctId,
                                          @PathVariable String authKey) {
        return detailService.view(acctId, authKey);
    }

    @PostMapping("/{acctId}/details/{authKey}/fraud")
    public PendingAuthDetailScreen fraud(@PathVariable Long acctId,
                                         @PathVariable String authKey) {
        return detailService.markFraud(acctId, authKey);
    }

    private PendingAuthResponse backwardPage(Long acctId, PendingAuthKey key) {
        List<PendingAuthDetail> fetched;
        try {
            fetched = detailRepository.findByAcctIdBeforeKey(acctId, key.date9c(),
                    key.time9c(), PageRequest.ofSize(PendingAuthService.PAGE_SIZE));
        } catch (DataAccessException exception) {
            return new PendingAuthResponse("page", null,
                    new PendingAuthScreenView(String.valueOf(acctId), "", "", "", "", "", "",
                            "", "", "", "", "", "", "", "",
                            new ArrayList<>(Collections.nCopies(PendingAuthPageState.ROW_COUNT, null)),
                            0, CobolMessages.pendingAuthDetailsError("EX")),
                    PendingAuthPageState.fresh(acctId));
        }
        List<PendingAuthDetail> ordered = new ArrayList<>(fetched);
        Collections.reverse(ordered);
        // Re-drive the forward fill at the recovered start key so rows and
        // look-ahead state come out in the same shape as a forward page.
        String startKey = ordered.isEmpty() ? null
                : PendingAuthKey.of(ordered.get(0)).encoded();
        return pendingAuthService.browse(startKey == null
                ? new PendingAuthRequest("ENTER", String.valueOf(acctId), null, null)
                : new PendingAuthRequest("PF7", null, null,
                        new PendingAuthPageState(acctId, 2, List.of(startKey), null, true,
                                new ArrayList<>(Collections.nCopies(
                                        PendingAuthPageState.ROW_COUNT, null)))));
    }
}
