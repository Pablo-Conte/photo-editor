package pdi.pablokaue.filters;

import java.awt.image.BufferedImage;

/**
 * Interface para um "ouvinte" que recebe atualizações
 * durante um processo de filtragem de várias etapas.
 */
public interface ProcessingStepListener {

    /**
     * Chamado quando uma etapa do processamento de imagem é concluída.
     *
     * @param stepName      O nome da etapa concluída (ex: "Threshold", "Detecção de Linhas").
     * @param resultImage   A imagem resultante após a conclusão da etapa.
     */
    void onStepCompleted(String stepName, BufferedImage resultImage);
}