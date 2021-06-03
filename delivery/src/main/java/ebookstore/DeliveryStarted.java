package ebookstore;

public class DeliveryStarted extends AbstractEvent {

    private Long id;
    private Long manageId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public Long getManageId() {
        return manageId;
    }

    public void setManageId(Long manageId) {
        this.manageId = manageId;
    }
}