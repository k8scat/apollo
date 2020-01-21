package com.ctrip.framework.apollo.portal.environment;

import com.ctrip.framework.apollo.core.utils.ApolloThreadFactory;
import com.ctrip.framework.apollo.core.utils.NetUtil;
import com.ctrip.framework.apollo.tracer.Tracer;
import com.ctrip.framework.apollo.tracer.spi.Transaction;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Only use in apollo-portal
 * Provider an available meta server url.
 * If there is no available meta server url for the given environment,
 * the default meta server url will be used(http://apollo.meta).
 * @see com.ctrip.framework.apollo.core.MetaDomainConsts
 * @author wxq
 */
public class PortalMetaDomainConsts {

    public static final String DEFAULT_META_URL = "http://apollo.meta";

    // env -> meta server address cache
    private static final Map<Env, String> metaServerAddressCache = Maps.newConcurrentMap();

    // initialize meta server provider without cache
    private static final PortalMetaServerProvider metaServerProvider = new PortalMetaServerProvider();

    private static final long REFRESH_INTERVAL_IN_SECOND = 60;// 1 min
    private static final Logger logger = LoggerFactory.getLogger(PortalMetaDomainConsts.class);
    // comma separated meta server address -> selected single meta server address cache
    private static final Map<String, String> selectedMetaServerAddressCache = Maps.newConcurrentMap();
    private static final AtomicBoolean periodicRefreshStarted = new AtomicBoolean(false);

    /**
     * Return one meta server address. If multiple meta server addresses are configured, will select one.
     */
    public static String getDomain(Env env) {
        String metaServerAddress = getMetaServerAddress(env);
        // if there is more than one address, need to select one
        if (metaServerAddress.contains(",")) {
            return selectMetaServerAddress(metaServerAddress);
        }
        return metaServerAddress;
    }

    /**
     * Return meta server address. If multiple meta server addresses are configured, will return the comma separated string.
     */
    public static String getMetaServerAddress(Env env) {
        // in cache?
        if (!metaServerAddressCache.containsKey(env)) {
            // put it to cache
            metaServerAddressCache.put(
                    env,
                    getMetaServerAddressCacheValue(metaServerProvider, env)
            );
        }

        // get from cache
        return metaServerAddressCache.get(env);
    }

    /**
     * Get the meta server from provider by given environment.
     * If there is no available meta server url for the given environment,
     * the default meta server url will be used(http://apollo.meta).
     * @param provider
     * @param env
     * @return
     */
    private static String getMetaServerAddressCacheValue(PortalMetaServerProvider provider, Env env) {
        String metaAddress = provider.getMetaServerAddress(env);
        logger.info("Located meta server address [{}] for env [{}]", metaAddress, env);

        if (Strings.isNullOrEmpty(metaAddress)) {
            // Fallback to default meta address
            metaAddress = DEFAULT_META_URL;
            logger.warn(
                    "Meta server address fallback to [{}] for env [{}], because it is not available in MetaServerProvider",
                    metaAddress, env);
        }
        return metaAddress.trim();
    }

    /**
     * Select one available meta server from the comma separated meta server addresses, e.g.
     * http://1.2.3.4:8080,http://2.3.4.5:8080
     *
     * <br />
     *
     * In production environment, we still suggest using one single domain like http://config.xxx.com(backed by software
     * load balancers like nginx) instead of multiple ip addresses
     */
    private static String selectMetaServerAddress(String metaServerAddresses) {
        String metaAddressSelected = selectedMetaServerAddressCache.get(metaServerAddresses);
        if (metaAddressSelected == null) {
            // initialize
            if (periodicRefreshStarted.compareAndSet(false, true)) {
                schedulePeriodicRefresh();
            }
            updateMetaServerAddresses(metaServerAddresses);
            metaAddressSelected = selectedMetaServerAddressCache.get(metaServerAddresses);
        }

        return metaAddressSelected;
    }

    private static void updateMetaServerAddresses(String metaServerAddresses) {
        logger.debug("Selecting meta server address for: {}", metaServerAddresses);

        Transaction transaction = Tracer.newTransaction("Apollo.MetaService", "refreshMetaServerAddress");
        transaction.addData("Url", metaServerAddresses);

        try {
            List<String> metaServers = Lists.newArrayList(metaServerAddresses.split(","));
            // random load balancing
            Collections.shuffle(metaServers);

            boolean serverAvailable = false;

            for (String address : metaServers) {
                address = address.trim();
                //check whether /services/config is accessible
                if (NetUtil.pingUrl(address + "/services/config")) {
                    // select the first available meta server
                    selectedMetaServerAddressCache.put(metaServerAddresses, address);
                    serverAvailable = true;
                    logger.debug("Selected meta server address {} for {}", address, metaServerAddresses);
                    break;
                }
            }

            // we need to make sure the map is not empty, e.g. the first update might be failed
            if (!selectedMetaServerAddressCache.containsKey(metaServerAddresses)) {
                selectedMetaServerAddressCache.put(metaServerAddresses, metaServers.get(0).trim());
            }

            if (!serverAvailable) {
                logger.warn("Could not find available meta server for configured meta server addresses: {}, fallback to: {}",
                        metaServerAddresses, selectedMetaServerAddressCache.get(metaServerAddresses));
            }

            transaction.setStatus(Transaction.SUCCESS);
        } catch (Throwable ex) {
            transaction.setStatus(ex);
            throw ex;
        } finally {
            transaction.complete();
        }
    }

    private static void schedulePeriodicRefresh() {
        ScheduledExecutorService scheduledExecutorService =
                Executors.newScheduledThreadPool(1, ApolloThreadFactory.create("MetaServiceLocator", true));

        scheduledExecutorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    for (String metaServerAddresses : selectedMetaServerAddressCache.keySet()) {
                        updateMetaServerAddresses(metaServerAddresses);
                    }
                } catch (Throwable ex) {
                    logger.warn(String.format("Refreshing meta server address failed, will retry in %d seconds",
                            REFRESH_INTERVAL_IN_SECOND), ex);
                }
            }
        }, REFRESH_INTERVAL_IN_SECOND, REFRESH_INTERVAL_IN_SECOND, TimeUnit.SECONDS);
    }

}