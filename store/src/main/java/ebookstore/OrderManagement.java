package ebookstore;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;
import java.util.List;
import java.util.Date;

@Entity
@Table(name="OrderManagement_table")
public class OrderManagement {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private Long orderId;
    private Long productId;
    private Integer qty;
    private String destination;
    private String status;
    private Long customerId;

    @PostPersist
    public void onPostPersist(){
        Created created = new Created();
        BeanUtils.copyProperties(this, created);
        created.publishAfterCommit();


    }

    @PostUpdate
    public void onPostUpdate(){
        OrderAppoved orderAppoved = new OrderAppoved();
        BeanUtils.copyProperties(this, orderAppoved);
        orderAppoved.publishAfterCommit();


        OrderDeleted orderDeleted = new OrderDeleted();
        BeanUtils.copyProperties(this, orderDeleted);
        orderDeleted.publishAfterCommit();


    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }
    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }
    public Integer getQty() {
        return qty;
    }

    public void setQty(Integer qty) {
        this.qty = qty;
    }
    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }




}
