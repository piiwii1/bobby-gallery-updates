package ch.piiwii.visited;

public class Place {
    public long id;
    public String name;
    public double lat;
    public double lon;
    public String source;

    public Place(long id, String name, double lat, double lon, String source) {
        this.id = id;
        this.name = name;
        this.lat = lat;
        this.lon = lon;
        this.source = source;
    }
}
