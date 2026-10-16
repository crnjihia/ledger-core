package ke.ledgercore.dto;

import java.util.List;

public class StatementResponse {
    private List<StatementEntryDTO> entries;
    private int page;
    private int totalPages;
    private long totalElements;

    public StatementResponse() {
    }

    public StatementResponse(List<StatementEntryDTO> entries, int page, int totalPages, long totalElements) {
        this.entries = entries;
        this.page = page;
        this.totalPages = totalPages;
        this.totalElements = totalElements;
    }

    // getters & setters
    public List<StatementEntryDTO> getEntries() {return entries;}
    public void setEntries(List<StatementEntryDTO> entries) {this.entries = entries;}
    public int getPage() {return page;}
    public void setPage(int page) {this.page = page;}
    public int getTotalPages() {return totalPages;}
    public void setTotalPages(int totalPages) {this.totalPages = totalPages;}
    public long getTotalElements() {return totalElements;}
    public void setTotalElements(long totalElements) {this.totalElements = totalElements;}
}
