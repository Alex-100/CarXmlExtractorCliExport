package pro.dralex.CarXmlExtractor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface CarModelRepo extends JpaRepository<CarModelConnector, Long> {
}
