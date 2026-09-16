package com.carddemo.api;

/**
 * Browse outcome: "page" renders the screen, "exit" is the PF3 return to the
 * caller, "navigate" is the PF2 XCTL to COTRTUPC.
 */
public record TranTypeListResponse(
        String outcome,
        TranTypeListScreen screen,
        TranTypeListPageState pageState,
        TranTypeNavigation navigation) {

    public static TranTypeListResponse page(TranTypeListScreen screen,
                                            TranTypeListPageState pageState) {
        return new TranTypeListResponse("page", screen, pageState, null);
    }
}
