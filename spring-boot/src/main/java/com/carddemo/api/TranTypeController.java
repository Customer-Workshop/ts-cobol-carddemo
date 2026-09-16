package com.carddemo.api;

import com.carddemo.service.TranTypeService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * COTRTLIC/COTRTUPC REST surface (S-21): GET is the keyed browse, POST
 * /list is one call per AID press echoing the COMMAREA as pageState, POST
 * /maint is one call per AID press echoing the maint COMMAREA, and the
 * CRUD verbs cover the single-record writes the screens compose.
 */
@RestController
@RequestMapping("/api/tran-types")
public class TranTypeController {

    private final TranTypeService service;

    public TranTypeController(TranTypeService service) {
        this.service = service;
    }

    @GetMapping
    public TranTypeListResponse list(@RequestParam(required = false) String type,
                                     @RequestParam(required = false) String desc,
                                     @RequestParam(required = false) String after,
                                     @RequestParam(required = false) String dir,
                                     Authentication authentication) {
        return service.list(authentication, type, desc, after, dir);
    }

    @PostMapping("/list")
    public TranTypeListResponse listAid(@RequestBody TranTypeListRequest request,
                                        Authentication authentication) {
        return service.browse(authentication, request);
    }

    @PostMapping("/maint")
    public TranTypeMaintResponse maintain(@RequestBody TranTypeMaintRequest request,
                                          Authentication authentication) {
        return service.maintain(authentication, request);
    }

    @GetMapping("/{code}")
    public TranTypeResponse detail(@PathVariable String code,
                                   Authentication authentication) {
        return service.detail(authentication, code);
    }

    @PostMapping
    public TranTypeResponse create(@RequestBody TranTypeCrudRequest request,
                                   Authentication authentication) {
        return service.create(authentication, request);
    }

    @PutMapping("/{code}")
    public TranTypeResponse update(@PathVariable String code,
                                   @RequestBody TranTypeCrudRequest request,
                                   Authentication authentication) {
        return service.update(authentication, code, request);
    }

    @DeleteMapping("/{code}")
    public void delete(@PathVariable String code,
                       Authentication authentication) {
        service.delete(authentication, code);
    }
}
