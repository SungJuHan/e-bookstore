package ebookstore;

import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource(collectionResourceRel="orderManagements", path="orderManagements")
public interface OrderManagementRepository extends PagingAndSortingRepository<OrderManagement, Long>{


}
