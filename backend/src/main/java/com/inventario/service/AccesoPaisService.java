package com.inventario.service;

import com.inventario.domain.Pais;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AccesoPaisService {

    public void validar(Set<String> paisesPermitidos, String codigoPais) {
        if (paisesPermitidos != null
                && !paisesPermitidos.isEmpty()
                && !paisesPermitidos.contains(codigoPais)) {
            throw new ErrorAplicacionException(
                    HttpStatus.FORBIDDEN, "error.country.access.denied");
        }
    }

    public void validar(Set<String> paisesPermitidos, Pais pais) {
        if (pais == null) {
            if (paisesPermitidos != null && !paisesPermitidos.isEmpty()) {
                throw new ErrorAplicacionException(
                        HttpStatus.FORBIDDEN, "error.country.access.denied");
            }
            return;
        }
        validar(paisesPermitidos, pais.codigoIso());
    }
}
