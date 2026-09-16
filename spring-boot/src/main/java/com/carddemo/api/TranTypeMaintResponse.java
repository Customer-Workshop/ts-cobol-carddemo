package com.carddemo.api;

/**
 * Maint outcome: "page" renders the screen, "exit" is the PF3 return to the
 * calling program.
 */
public record TranTypeMaintResponse(
        String outcome,
        TranTypeMaintScreen screen,
        TranTypeMaintState state,
        TranTypeNavigation navigation) {

    public static TranTypeMaintResponse page(TranTypeMaintScreen screen,
                                             TranTypeMaintState state) {
        return new TranTypeMaintResponse("page", screen, state, null);
    }
}
