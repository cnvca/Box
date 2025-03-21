public class ItvDns extends NanoHTTPD {
    public ItvDns() throws IOException {
        super(9978);
    }

    @Override
    public Response serve(IHTTPSession session) {
        return Response.newFixedLengthResponse("Hello, World!");
    }

    public static void main(String[] args) throws IOException {
        ItvDns server = new ItvDns();
        server.start();
    }
}
