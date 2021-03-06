package com.datalex.eventia.service;

import com.datalex.eventia.ApplicationProperties;
import com.datalex.eventia.converter.AirShoppingRSOfferConverterService;
import com.datalex.eventia.converter.ConvertService;
import com.datalex.eventia.domain.*;
import com.datalex.eventia.dto.predictHQ.Event;
import org.iata.iata.edist.AirShopReqAttributeQueryType;
import org.iata.iata.edist.AirShoppingRQ;
import org.iata.iata.edist.AirShoppingRS;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.stereotype.Service;

import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;

/**
 * Created by shaojie.xu on 20/05/2017.
 */
@Service
public class OfferService {

    @Autowired
    AirShoppingService airShoppingService;

    @Autowired
    AirShoppingRSOfferConverterService airShopingRSOfferConverterService;

    @Autowired
    AirportLocatingService airportLocatingService;

    @Autowired
    private Jaxb2Marshaller unmarshaller;

    @Autowired
    EventService predictHQEventService;

    @Autowired
    private ApplicationProperties properties;

    @Autowired
    private HotelService hotelService;

    @Autowired
    IndigoService indigoService;


    @Autowired
    private ResourceLoader resourceLoader;


    Object unmarshalObject(String filePath) throws IOException {
        try (InputStream is = resourceLoader.getResource(buildPath(filePath)).getInputStream()) {
            return unmarshaller.unmarshal(new StreamSource(is));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    private static String buildPath(String fileName) {
        StringJoiner joiner = new StringJoiner(":");
        return joiner.add("classpath").add(fileName).toString();
    }

    public Offer getBestOffer(String origin, String eventId){

        Event event = predictHQEventService.getEventById(eventId);

        if(event == null)
        {
            throw new IllegalArgumentException("Event not found : " + eventId);
        }

        Event e = predictHQEventService.getEventById(eventId);
        String destination = findClosestJetBlueAirport(e);
        AirShoppingRQ rq = getAirShoppingRQ(origin, e, destination);

        AirShoppingRS flights = airShoppingService.findFlights(rq);
        List<Hotel> hotels = hotelService.findHotels(event);

        Offer offer = airShopingRSOfferConverterService.convert(flights, origin, destination);
        String date = offer.getFlights().get(0).getOriginDestinations().get(0).getArrival().getDate();
        List<Taxi> taxis = indigoService.getTaxis("B624", date, "40.780593", "-73.98131");
        offer.setAncillaries(createDummyAncillaries());
        offer.setHotels(hotels);
        offer.setTaxis(taxis);
        return offer;
    }

    private List<Ancillary> createDummyAncillaries() {
        List<Ancillary> list = new ArrayList<>();
        Ancillary anc = new Ancillary();
        anc.setDescription("Even more seats");
        anc.setPrice("20");
        list.add(anc);
        anc = new Ancillary();
        anc.setDescription("Even more speed");
        anc.setPrice("40");
        list.add(anc);
        return list;
    }

    private List<Taxi> createDummyTaxis() {
        List<Taxi> taxis = new ArrayList<>();
        Taxi taxi = new Taxi();
        taxi.setFlightId("111");
        taxi.setPickupTime("14:00");
        taxi.setPrice(new BigDecimal("10"));
        taxis.add(taxi);
        taxi = new Taxi();
        taxi.setFlightId("222");
        taxi.setPickupTime("17:00");
        taxi.setPrice(new BigDecimal("15"));
        taxis.add(taxi);
        return taxis;
    }

    private List<Hotel> createDummyHotels() {
        List<Hotel> hotels = new ArrayList<>();
        Hotel hotel = new Hotel();
        hotel.setDistanceToPlace("1km");
        hotel.setNights(3);
        hotel.setPrice(new BigDecimal("234"));
        hotel.setRoomStay("Double room");
        hotel.setStars("3");
        hotels.add(hotel);
        hotel = new Hotel();
        hotel.setDistanceToPlace("1km");
        hotel.setNights(3);
        hotel.setPrice(new BigDecimal("483"));
        hotel.setRoomStay("Double room");
        hotel.setStars("4");
        hotel = new Hotel();
        return hotels;
    }


    public AirShoppingRQ getAirShoppingRQ(String origin, Event e, String airport) {
        ZoneId zonedIdDestination = ZoneId.of(e.getTimezone());
        LocalDate eventStartLocalDate = e.getStart().toInstant().atZone(zonedIdDestination).toLocalDate();
        LocalDate eventEndLocalDate = e.getEnd().toInstant().atZone(zonedIdDestination).toLocalDate();

        AirShoppingRQ rq = null;
        try {
            rq = (AirShoppingRQ) unmarshalObject("request.xml");
        } catch (IOException e1) {
            e1.printStackTrace();
        }

        AirShopReqAttributeQueryType.OriginDestination outbound = rq.getCoreQuery().getOriginDestinations().getOriginDestination().get(0);
        outbound.getDeparture().getAirportCode().setValue(origin);
        outbound.getArrival().getAirportCode().setValue(airport);
        outbound.getDeparture().setDate(eventStartLocalDate.minusDays(1));

        AirShopReqAttributeQueryType.OriginDestination inbound = rq.getCoreQuery().getOriginDestinations().getOriginDestination().get(1);
        inbound.getDeparture().getAirportCode().setValue(airport);
        inbound.getArrival().getAirportCode().setValue(origin);
        inbound.getDeparture().setDate(eventEndLocalDate.plusDays(1));

        return rq;
    }

    private String findClosestJetBlueAirport(Event e) {
        String lon = e.getLocation().get(0);
        String lat = e.getLocation().get(1);
        Coordinate coordinate = new Coordinate(lon, lat);
        List<String> airports = airportLocatingService.localNearestAirports(coordinate);

        return airports.stream()
                .filter(ap -> properties.getPreLoadedCities().contains(ap))
                .findFirst()
                .orElse("");
    }


    public static void main(String... args){

        ZoneId id1 = ZoneId.of("America/New_York");
        System.out.println(id1);
    }


}
