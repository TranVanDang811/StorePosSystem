package com.possystem.backend.common.util.mapper;

import com.possystem.backend.report.dto.TopProductDTO;
import org.mapstruct.Mapper;

import java.math.BigDecimal;
import java.util.List;


@Mapper(componentModel = "spring")
public interface ReportMapper {

    default TopProductDTO toTopProductDTO(Object[] row) {
        if (row == null) return null;

        return TopProductDTO.builder()
                .productName((String) row[0])
                .quantity(((Number) row[1]).intValue())
                .revenue((BigDecimal) row[2])
                .build();
    }

    List<TopProductDTO> toTopProductDTOList(List<Object[]> rows);

}

