package it.gov.pagopa.nodetsworker.service.mapper;

import it.gov.pagopa.nodetsworker.repository.model.EventEntity;
import it.gov.pagopa.nodetsworker.service.dto.EventDto;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants.ComponentModel;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(componentModel = ComponentModel.JAKARTA)
public interface EventMapper {

  EventMapper INSTANCE = Mappers.getMapper(EventMapper.class);

  EventDto map(EventEntity entity);
  List<EventDto> map(List<EventEntity> entities);

}
