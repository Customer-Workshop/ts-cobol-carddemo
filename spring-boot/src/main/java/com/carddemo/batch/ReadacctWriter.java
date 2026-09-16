package com.carddemo.batch;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamWriter;
import org.springframework.batch.item.file.FlatFileItemWriter;

import java.util.ArrayList;
import java.util.List;

/**
 * Writes each ReadacctOutputs item to its three files: one PSCOMP line, one
 * ARRYPS line, then the VB1 and VB2 variable-length lines (in that order,
 * matching 1550-WRITE-VB1-RECORD / 1575-WRITE-VB2-RECORD).
 */
public class ReadacctWriter implements ItemStreamWriter<ReadacctOutputs> {
    private final FlatFileItemWriter<String> pscomp;
    private final FlatFileItemWriter<String> arryps;
    private final FlatFileItemWriter<String> vbps;

    public ReadacctWriter(FlatFileItemWriter<String> pscomp,
                          FlatFileItemWriter<String> arryps,
                          FlatFileItemWriter<String> vbps) {
        this.pscomp = pscomp;
        this.arryps = arryps;
        this.vbps = vbps;
    }

    @Override
    public void write(Chunk<? extends ReadacctOutputs> chunk) throws Exception {
        List<String> pscompLines = new ArrayList<>();
        List<String> arrypsLines = new ArrayList<>();
        List<String> vbpsLines = new ArrayList<>();
        for (ReadacctOutputs outputs : chunk) {
            // FACT-pending (S17-B7): layouts are exactly LRECL with a 7-byte
            // COMP-3 field; pad() keeps the record at the JCL LRECL if a
            // field ever drifts short.
            pscompLines.add(BatchFileSupport.pad(outputs.pscomp(), 107));
            arrypsLines.add(BatchFileSupport.pad(outputs.arryps(), 110));
            vbpsLines.add(outputs.vb1());
            vbpsLines.add(outputs.vb2());
        }
        pscomp.write(new Chunk<>(pscompLines));
        arryps.write(new Chunk<>(arrypsLines));
        vbps.write(new Chunk<>(vbpsLines));
    }

    @Override
    public void open(ExecutionContext context) {
        pscomp.open(context);
        arryps.open(context);
        vbps.open(context);
    }

    @Override
    public void update(ExecutionContext context) {
        pscomp.update(context);
        arryps.update(context);
        vbps.update(context);
    }

    @Override
    public void close() {
        pscomp.close();
        arryps.close();
        vbps.close();
    }
}
