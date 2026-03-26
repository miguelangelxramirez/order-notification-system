package es.tirea.notificationservice.domain.port.out;

public interface EmbeddingPort {

    double[] embed(String input);

    String modelName();
}
