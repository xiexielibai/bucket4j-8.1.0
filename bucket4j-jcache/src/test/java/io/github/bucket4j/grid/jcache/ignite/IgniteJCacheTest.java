
package io.github.bucket4j.grid.jcache.ignite;

import io.github.bucket4j.grid.jcache.AbstractJCacheTest;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.gridkit.nanocloud.Cloud;
import org.gridkit.nanocloud.CloudFactory;
import org.gridkit.nanocloud.VX;
import org.gridkit.vicluster.ViNode;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import javax.cache.Cache;
import java.io.Serializable;
import java.net.UnknownHostException;
import java.util.Collections;

public class IgniteJCacheTest extends AbstractJCacheTest {

    private static Cache<String, byte[]> cache;
    private static Cloud cloud;
    private static ViNode server;

    private static Ignite ignite;

    @BeforeClass
    public static void setup() throws UnknownHostException {
        // start separated JVM on current host
        cloud = CloudFactory.createCloud();
        cloud.node("**").x(VX.TYPE).setLocal();
        server = cloud.node("stateful-ignite-server");

        int serverDiscoveryPort = 47500;
//        String serverNodeAdress = InetAddress.getLocalHost().getHostAddress() + ":" + serverDiscoveryPort;
        String serverNodeAdress = "localhost:" + serverDiscoveryPort;

        server.exec((Runnable & Serializable) () -> {
            TcpDiscoveryVmIpFinder neverFindOthers = new TcpDiscoveryVmIpFinder();
            neverFindOthers.setAddresses(Collections.singleton(serverNodeAdress));

            TcpDiscoverySpi tcpDiscoverySpi = new TcpDiscoverySpi();
            tcpDiscoverySpi.setIpFinder(neverFindOthers);
            tcpDiscoverySpi.setLocalPort(serverDiscoveryPort);

            IgniteConfiguration igniteConfiguration = new IgniteConfiguration();
            igniteConfiguration.setClientMode(false);
            igniteConfiguration.setDiscoverySpi(tcpDiscoverySpi);

            CacheConfiguration cacheConfiguration = new CacheConfiguration("my_buckets");
            Ignite ignite = Ignition.start(igniteConfiguration);
            ignite.getOrCreateCache(cacheConfiguration);
        });

        // start ignite client which works inside current JVM and does not hold data
        TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
        ipFinder.setAddresses(Collections.singleton(serverNodeAdress));
        TcpDiscoverySpi tcpDiscoverySpi = new TcpDiscoverySpi();
        tcpDiscoverySpi.setIpFinder(ipFinder);

        IgniteConfiguration igniteConfiguration = new IgniteConfiguration();
        igniteConfiguration.setDiscoverySpi(tcpDiscoverySpi);
        igniteConfiguration.setClientMode(true);
        ignite = Ignition.start(igniteConfiguration);
        CacheConfiguration cacheConfiguration = new CacheConfiguration("my_buckets");
        cache = ignite.getOrCreateCache(cacheConfiguration);
    }

    @AfterClass
    public static void shutdown() {
        if (ignite != null) {
            ignite.close();
        }
        if (cloud != null) {
            cloud.shutdown();
        }
    }

    @Override
    protected Cache<String, byte[]> getCache() {
        return cache;
    }

}
