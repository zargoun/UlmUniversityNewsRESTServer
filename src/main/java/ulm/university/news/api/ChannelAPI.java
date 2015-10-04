package ulm.university.news.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.key.ZonedDateTimeKeyDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ulm.university.news.controller.ChannelController;
import ulm.university.news.data.*;
import ulm.university.news.data.enums.ChannelType;
import ulm.university.news.util.PATCH;
import ulm.university.news.util.exceptions.ServerException;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.net.URI;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

import static ulm.university.news.util.Constants.*;

/**
 * The ChannelAPI is responsible for accepting incoming channel requests, reading the required data and handing
 * it over to the appropriate controller methods. After the request has been executed successfully, the ChannelAPI
 * generates the response message. If the execution of the request has failed for whatever reasons, the
 * ServerException is handed over to the ErrorHandler class.
 *
 * @author Matthias Mak
 * @author Philipp Speidel
 */
@Path("/channel")
public class ChannelAPI {

    /** Instance of the ChannelController class. */
    private ChannelController channelCtrl = new ChannelController();

    /** The logger instance for ChannelAPI. */
    private static final Logger logger = LoggerFactory.getLogger(ChannelAPI.class);

    /**
     * Create a new channel and adds the creator to its responsible moderators. The data of the new channel is
     * provided within the JSON String. The appropriate channel subclass will be created from the JSON representation.
     * The created resource will be returned including the URI which can be used to access the resource.
     *
     * @param accessToken The access token of the requestor.
     * @param json The JSON String of a channel which is contained in the body of the HTTP request.
     * @param uriInfo Information about the URI of this request.
     * @return Response object including the created channel object and a set Location Header.
     * @throws ServerException If the execution of the POST request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createChannel(@HeaderParam("Authorization") String accessToken, @Context UriInfo uriInfo, String
            json) throws ServerException {
        // Create appropriate channel object from JSON String.
        Channel channel = getChannelFromJSON(json);
        channel = channelCtrl.createChannel(accessToken, channel);
        // Create the URI for the created channel resource.
        URI createdURI = URI.create(uriInfo.getBaseUri().toString() + "channel" + "/" + channel.getId());
        // Return the created channel resource and the Location Header.
        return Response.status(Response.Status.CREATED).contentLocation(createdURI).entity(channel).build();
    }

    /**
     * Changes an existing channel and possibly its subclass. The data which should be changed is provided within the
     * JSON String from which an appropriate channel object will be created. The changed channel resource will be
     * returned to the requestor.
     *
     * @param accessToken The access token of the requestor.
     * @param channelId The id of the channel which should be changed.
     * @param json The JSON String of a channel which is contained in the body of the HTTP request.
     * @return Response object including the changed channel data.
     * @throws ServerException If the execution of the PATCH request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @PATCH
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{id}")
    public Response changeChannel(@HeaderParam("Authorization") String accessToken, @PathParam("id") int channelId,
                                  String json) throws ServerException {
        // Create appropriate channel object from JSON String.
        Channel channel = getChannelFromJSON(json);
        channel = channelCtrl.changeChannel(accessToken, channelId, channel);
        // Return the changed channel resource.
        return Response.status(Response.Status.OK).entity(channel).build();
    }


    /**
     * Delivers all the existing channel data. The requested channels can be restricted to a specific selection by the
     * given query params.
     *
     * @param accessToken The access token of the requestor.
     * @param moderatorId Get only channels for which the moderator with the given id is responsible.
     * @param lastUpdated Get only channels with a newer modification data as the last updated date.
     * @return Response object including a list with channel data.
     * @throws ServerException If the execution of the GET request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public String getChannels(@HeaderParam("Authorization") String accessToken, @QueryParam("moderatorId")
    Integer moderatorId, @QueryParam("lastUpdated") String lastUpdated) throws ServerException {
        // TODO Note: On client site replace + in date String with %2B
        // TODO Replace other reserved characters too? https://de.wikipedia.org/wiki/URL-Encoding
        ZonedDateTime lastUpdatedDate = null;
        try {
            // Verify correct date format.
            if (lastUpdated != null) {
                lastUpdatedDate = ZonedDateTime.parse(lastUpdated);
            }
        } catch (DateTimeParseException e) {
            logger.error(LOG_SERVER_EXCEPTION, 400, PARSING_FAILURE, "Couldn't parse date String.");
            throw new ServerException(400, PARSING_FAILURE);
        }
        // Get all the requested channel resources.
        List<Channel> channels = channelCtrl.getChannels(accessToken, moderatorId, lastUpdatedDate);

        // Write channels with subclass attributes as JSON String.
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        // Make sure that dates are formatted correctly.
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(SerializationFeature.WRITE_DATES_WITH_ZONE_ID, true);
        try {
            return mapper.writeValueAsString(channels);
        } catch (JsonProcessingException e) {
            logger.error(LOG_SERVER_EXCEPTION, 500, PARSING_FAILURE, "Couldn't parse channels to JSON String.");
            throw new ServerException(500, PARSING_FAILURE);
        }
    }

    /**
     * Delivers the channel data of a specific channel identified by id.
     *
     * @param accessToken The access token of the requestor.
     * @param channelId The id of the channel.
     * @return Response object including a the channel data.
     * @throws ServerException If the execution of the GET request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getChannel(@HeaderParam("Authorization") String accessToken, @PathParam("id") int channelId) throws
            ServerException {
        // Get the requested channel resource.
        Channel channel = channelCtrl.getChannel(accessToken, channelId);
        // Return the channel resource.
        return Response.status(Response.Status.OK).entity(channel).build();
    }

    /**
     * Deletes a channel from the database. Afterwards, it's no longer available on the server.
     *
     * @param accessToken The access token of the requestor.
     * @param channelId The id of the channel which should be deleted.
     * @return Response object.
     * @throws ServerException If the execution of the DELETE request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @DELETE
    @Path("/{id}")
    public Response deleteChannel(@HeaderParam("Authorization") String accessToken, @PathParam("id") int channelId)
            throws ServerException {
        channelCtrl.deleteChannel(accessToken, channelId);
        // Return 204 No Content
        return Response.status(Response.Status.NO_CONTENT).build();
    }

    /**
     * Adds a moderator to a channel. Afterwards the moderator is registered as responsible moderator for the channel.
     *
     * @param accessToken The access token of the requestor.
     * @param channelId The id of the channel to which the moderator should be added.
     * @param moderator The moderator (including the name) who should be added to the channel.
     * @return Response object.
     * @throws ServerException If the execution of the POST request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @POST
    @Path("/{id}/moderator")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response addModeratorToChannel(@HeaderParam("Authorization") String accessToken, @PathParam("id") int
            channelId, Moderator moderator) throws ServerException {
        channelCtrl.addModeratorToChannel(accessToken, channelId, moderator.getName());
        // Return 201 Created TODO Set Location Header?
        return Response.status(Response.Status.CREATED).build();
    }

    /**
     * Removes a moderator from a channel. Afterwards the moderator is no longer responsible for the channel.
     *
     * @param accessToken The access token of the requestor.
     * @param channelId The id of the channel for which the moderator is responsible.
     * @param moderatorId The id of the moderator who should be removed as responsible moderator from the channel.
     * @return Response object.
     * @throws ServerException If the execution of the DELETE request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @DELETE
    @Path("/{channelId}/moderator/{moderatorId}")
    public Response removeModeratorFromChannel(@HeaderParam("Authorization") String accessToken, @PathParam("channelId")
    int channelId, @PathParam("moderatorId") int moderatorId) throws ServerException {
        channelCtrl.removeModeratorFromChannel(accessToken, channelId, moderatorId);
        // Return 204 No Content
        return Response.status(Response.Status.NO_CONTENT).build();
    }

    /**
     * Delivers the moderator data of all moderators who are responsible for the channel with the given id.
     *
     * @param accessToken The access token of the requestor.
     * @param channelId The id of the channel.
     * @return Response object including a list with moderator data.
     * @throws ServerException If the execution of the GET request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @GET
    @Path("/{id}/moderator")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Moderator> getResponsibleModerators(@HeaderParam("Authorization") String accessToken, @PathParam
            ("id") int channelId) throws ServerException {
        // Return all the requested moderator resources.
        return channelCtrl.getResponsibleModerators(accessToken, channelId);
    }

    /**
     * Adds a user to a channel. Afterwards the user is registered as subscriber of the channel.
     *
     * @param accessToken The access token of the requestor.
     * @param channelId The id of the channel to which the user should be added.
     * @return Response object.
     * @throws ServerException If the execution of the POST request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @POST
    @Path("/{id}/user")
    public Response subscribeChannel(@HeaderParam("Authorization") String accessToken, @PathParam("id") int
            channelId) throws ServerException {
        channelCtrl.subscribeChannel(accessToken, channelId);
        // Return 201 Created TODO Set Location Header?
        return Response.status(Response.Status.CREATED).build();
    }

    /**
     * Removes a user from a channel. Afterwards the user is no longer a subscriber of the channel.
     *
     * @param accessToken The access token of the requestor.
     * @param channelId The id of the channel to which the user is subscribed.
     * @param userId The id of the user who should be removed as subscriber from the channel.
     * @return Response object.
     * @throws ServerException If the execution of the DELETE request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @DELETE
    @Path("/{channelId}/user/{userId}")
    public Response unsubscribeChannel(@HeaderParam("Authorization") String accessToken, @PathParam("channelId") int
            channelId, @PathParam("userId") int userId) throws ServerException {
        channelCtrl.unsubscribeChannel(accessToken, channelId, userId);
        // Return 204 No Content
        return Response.status(Response.Status.NO_CONTENT).build();
    }

    /**
     * Delivers the user data of all users who are subscribed to the channel with the given id.
     *
     * @param accessToken The access token of the requestor.
     * @param channelId The id of the channel.
     * @return Response object including a list with user data.
     * @throws ServerException If the execution of the GET request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @GET
    @Path("/{id}/user")
    @Produces(MediaType.APPLICATION_JSON)
    public List<User> getSubscribers(@HeaderParam("Authorization") String accessToken, @PathParam
            ("id") int channelId) throws ServerException {
        // Return all the requested user resources.
        return channelCtrl.getSubscribers(accessToken, channelId);
    }

    /**
     * Creates a channel object from a given JSON String. The created object may be a subclass of channel. The JSON
     * String has to provide a valid channel type.
     *
     * @param json The JSON String of a channel which is contained in the body of the HTTP request.
     * @return The channel object created from the JSON String.
     * @throws ServerException If an invalid channel type is provided or a parsing exception occurs.
     */
    private Channel getChannelFromJSON(String json) throws ServerException {
        logger.debug("Start with JSON String:{}", json);
        Channel channel;
        try {
            // Read channel type form JSON to determine channel subclass.
            ObjectMapper mapper = new ObjectMapper();
            // Set fields and Enum values which are unknown to null and continue parsing.
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            mapper.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true);
            String type = mapper.readTree(json).get("type").asText();
            ChannelType channelType;
            try {
                channelType = ChannelType.valueOf(type);
            } catch (IllegalArgumentException e) {
                // Invalid channel type. Abort method.
                logger.error(LOG_SERVER_EXCEPTION, 400, CHANNEL_INVALID_TYPE, "Channel type is invalid.");
                throw new ServerException(400, CHANNEL_INVALID_TYPE);
            }

            // Generate appropriate channel subclass from JSON representation.
            switch (channelType) {
                case LECTURE:
                    channel = mapper.readValue(json, Lecture.class);
                    logger.debug("Created channel subclass Lecture.");
                    break;
                case EVENT:
                    channel = mapper.readValue(json, Event.class);
                    logger.debug("Created channel subclass Event.");
                    break;
                case SPORTS:
                    channel = mapper.readValue(json, Sports.class);
                    logger.debug("Created channel subclass Sports.");
                    break;
                default:
                    // There is no subclass for channel type OTHER and STUDENT_GROUP, so create normal channel object.
                    channel = mapper.readValue(json, Channel.class);
                    logger.debug("Created Channel class");
                    break;
            }
        } catch (IOException e) {
            logger.error(LOG_SERVER_EXCEPTION, 400, CHANNEL_INVALID_TYPE, "Channel type is invalid.");
            throw new ServerException(400, CHANNEL_INVALID_TYPE);
        }
        logger.debug("End with channel:{}.", channel);
        return channel;
    }

    /**
     * Create a new announcement in the specified channel. The created resource will be returned including the URI
     * which can be used to access the resource.
     *
     * @param accessToken The access token of the requestor.
     * @param announcement The announcement data contained in the body of the HTTP request.
     * @param uriInfo Information about the URI of this request.
     * @return Response object including the created announcement object and a set Location Header.
     * @throws ServerException If the execution of the POST request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @POST
    @Path("/{id}/announcement")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createAnnouncement(@HeaderParam("Authorization") String accessToken, @Context UriInfo uriInfo,
                                       @PathParam("id") int channelId, Announcement announcement) throws ServerException {
        announcement = channelCtrl.createAnnouncement(accessToken, channelId, announcement);
        // Create the URI for the created announcement resource.
        URI createdURI = URI.create(uriInfo.getBaseUri().toString() + "channel/" + channelId + "/announcement" +
                announcement.getId());
        // Return the created announcement resource and the Location Header.
        return Response.status(Response.Status.CREATED).contentLocation(createdURI).entity(announcement).build();
    }

    /**
     * Returns the announcements of the channel starting from a defined message number which is taken form the
     * request URL. The method returns a list of all announcements of the channel which have a higher message
     * number than the one defined in the request.
     *
     * @param accessToken The access token of the requestor.
     * @param channelId The id of the channel from which the announcements should be retrieved.
     * @param messageNumber The starting message number. All announcements of the channel which have a higher message
     * number than the one defined in this parameter are returned.
     * @return A list of announcements. The list can be empty.
     * @throws ServerException If the execution of the GET request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{id}/announcement")
    public List<Announcement> getAnnouncements(@HeaderParam("Authorization") String accessToken, @PathParam("id") int
            channelId, @DefaultValue("0") @QueryParam("messageNr") int messageNumber) throws ServerException {
        return channelCtrl.getAnnouncements(accessToken, channelId, messageNumber);
    }

    /**
     * Deletes an announcement from a channel. This method is used in combination with create a new announcement to
     * simulate a change of an announcement.
     *
     * @param accessToken The access token of the requestor.
     * @param channelId The id of the channel to which the user is subscribed.
     * @param messageNumber The message number of the announcement which should be deleted from the channel.
     * @return Response object.
     * @throws ServerException If the execution of the DELETE request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @DELETE
    @Path("/{channelId}/announcement/{messageNumber}")
    public Response deleteAnnouncement(@HeaderParam("Authorization") String accessToken, @PathParam("channelId") int
            channelId, @PathParam("messageNumber") int messageNumber) throws ServerException {
        channelCtrl.deleteAnnouncement(accessToken, channelId, messageNumber);
        // Return 204 No Content
        return Response.status(Response.Status.NO_CONTENT).build();
    }

    /**
     * Create a new reminder in the specified channel. The created resource will be returned including the URI
     * which can be used to access the resource.
     *
     * @param accessToken The access token of the requestor.
     * @param uriInfo Information about the URI of this request.
     * @param channelId The id of the channel in which the reminder should be created.
     * @param json The reminder data represented as JSON String.
     * @return Response object including the created reminder object and a set Location Header.
     * @throws ServerException If the execution of the POST request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @POST
    @Path("/{id}/reminder")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createReminder(@HeaderParam("Authorization") String accessToken, @Context UriInfo uriInfo,
                                   @PathParam("id") int channelId, String json) throws ServerException {
        Reminder reminder = getReminderFromJSON(json);
        reminder = channelCtrl.createReminder(accessToken, channelId, reminder);
        // Create the URI for the created reminder resource.
        URI createdURI = URI.create(uriInfo.getBaseUri().toString() + "channel/" + channelId + "/reminder" +
                reminder.getId());
        // Return the created reminder resource and the Location Header.
        return Response.status(Response.Status.CREATED).contentLocation(createdURI).entity(reminder).build();
    }

    /**
     * Changes an existing reminder in the specified channel. The updated resource will be returned to the requestor.
     *
     * @param accessToken The access token of the requestor.
     * @param channelId The id of the channel to which the reminder belongs.
     * @param reminderId The id of the reminder which should be changed.
     * @param json The changed reminder data represented as JSON String.
     * @return Response object including the changed reminder object.
     * @throws ServerException If the execution of the PATCH request has failed. The ServerException contains
     * information about the error which has occurred.
     */
    @PATCH
    @Path("/{channelId}/reminder/{reminderId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response changeReminder(@HeaderParam("Authorization") String accessToken, @PathParam("channelId") int
            channelId, @PathParam("reminderId") int reminderId, String json) throws ServerException {
        Reminder reminder = getReminderFromJSON(json);
        reminder = channelCtrl.changeReminder(accessToken, channelId, reminder, reminderId);
        // Return the updated reminder resource.
        return Response.status(Response.Status.OK).entity(reminder).build();
    }

    /**
     * Creates a reminder object from a given JSON String.
     *
     * @param json The reminder data represented as JSON String.
     * @return The reminder object created from JSON.
     * @throws ServerException If a parsing exception occurs.
     */
    private Reminder getReminderFromJSON(String json) throws ServerException {
        logger.debug("Start with JSON String:{}", json);
        // Use JavaTimeModule for proper deserialization of ZonedDateTime values.
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule().addKeyDeserializer(ZonedDateTime.class,
                ZonedDateTimeKeyDeserializer.INSTANCE));
        // Set fields and Enum values which are unknown to null and continue parsing.
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true);

        Reminder reminder;
        try {
            reminder = mapper.readValue(json, Reminder.class);
        } catch (IOException e) {
            logger.error(LOG_SERVER_EXCEPTION, 400, REMINDER_INVALID_DATES, "Parsing: Reminder dates are invalid.");
            throw new ServerException(400, REMINDER_INVALID_DATES);
        }
        // Set proper time zone.
        if (reminder != null && reminder.getStartDate() != null && reminder.getEndDate() != null) {
            reminder.setStartDate(reminder.getStartDate().withZoneSameInstant(TIME_ZONE));
            reminder.setEndDate(reminder.getEndDate().withZoneSameInstant(TIME_ZONE));
        }
        logger.debug("End with reminder:{}", reminder);
        return reminder;
    }
}
