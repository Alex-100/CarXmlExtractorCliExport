package pro.dralex.CarXmlExtractor;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table
public class CarModelConnector {
    @Id
    @GeneratedValue
    private Long id;
    private String make;
    private String model;
    private String avStyle;
    private String auStyle;
    private String drStyle;

    public CarModelConnector(String make, String model, String avStyle, String auStyle, String drStyle) {
        this.make = make;
        this.model = model;
        this.avStyle = avStyle;
        this.auStyle = auStyle;
        this.drStyle = drStyle;
    }

    public CarModelConnector(String make, String avStyle, String auStyle, String drStyle) {
        this.model = auStyle;

        this.make = make;
        this.avStyle = avStyle;
        this.auStyle = auStyle;
        this.drStyle = drStyle;
    }


}
