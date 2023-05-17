package pro.dralex.CarXmlExtractor;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table
public class CarMakeConnector {
    @Id
    @GeneratedValue
    private Long id;
    private String avStyle;
    private String auStyle;
    private String drStyle;

    public CarMakeConnector(String avStyle, String auStyle, String drStyle) {
        this.avStyle = avStyle;
        this.auStyle = auStyle;
        this.drStyle = drStyle;
    }


}
